package kombuchamc.createsecurity.client.compat;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class IrisFeedShaderHook implements FeedShaderHook {

    private static final String IRIS_API = "net.irisshaders.iris.api.v0.IrisApi";

    private static final String[][] LAYOUTS = {
            {"net.irisshaders.iris.Iris",
                    "net.irisshaders.iris.pipeline.PipelineManager",
                    "net.irisshaders.iris.pipeline.VanillaRenderingPipeline",
                    "net.irisshaders.iris.pipeline.WorldRenderingPipeline"},
            {"net.coderbot.iris.Iris",
                    "net.coderbot.iris.pipeline.PipelineManager",
                    "net.coderbot.iris.pipeline.FixedFunctionWorldRenderingPipeline",
                    "net.coderbot.iris.pipeline.WorldRenderingPipeline"}
    };

    private record Bindings(Method getPipelineManager, Method getCurrentDimension, Method preparePipeline,
                            Field pipelinesPerDimension, Class<?> vanillaClass,
                            Constructor<?> vanillaConstructor, Object irisApi, Method isShaderPackInUse,
                            Field worldRendererPipeline) {}

    private final Bindings bindings;

    private Object vanillaPipeline;
    private Object swappedKey;
    private Object savedPipeline;
    private boolean pipelineFieldInstalled;

    public IrisFeedShaderHook() throws ReflectiveOperationException {
        bindings = bind();
    }

    private static Bindings bind() throws ReflectiveOperationException {
        ReflectiveOperationException failure = null;
        for (String[] layout : LAYOUTS) {
            try {
                return bind(layout);
            } catch (ReflectiveOperationException e) {
                failure = e;
            }
        }
        throw failure;
    }

    private static Bindings bind(String[] layout) throws ReflectiveOperationException {
        ClassLoader loader = IrisFeedShaderHook.class.getClassLoader();
        Class<?> iris = Class.forName(layout[0], false, loader);
        Class<?> manager = Class.forName(layout[1], false, loader);
        Class<?> vanilla = Class.forName(layout[2], false, loader);
        Class<?> pipelineInterface = Class.forName(layout[3], false, loader);
        Class<?> api = Class.forName(IRIS_API, false, loader);

        Field worldRendererPipeline = null;
        for (Field field : net.minecraft.client.render.WorldRenderer.class.getDeclaredFields()) {
            if (field.getType() == pipelineInterface) worldRendererPipeline = field;
        }
        if (worldRendererPipeline == null) {
            throw new NoSuchFieldException("WorldRenderer has no " + layout[3] + " field");
        }
        worldRendererPipeline.setAccessible(true);

        Field pipelines = manager.getDeclaredField("pipelinesPerDimension");
        pipelines.setAccessible(true);

        Method prepare = null;
        for (Method method : manager.getMethods()) {
            if (method.getName().equals("preparePipeline") && method.getParameterCount() == 1) {
                prepare = method;
            }
        }
        if (prepare == null) throw new NoSuchMethodException(layout[1] + ".preparePipeline");

        return new Bindings(iris.getMethod("getPipelineManager"), iris.getMethod("getCurrentDimension"),
                prepare, pipelines, vanilla, vanilla.getConstructor(),
                api.getMethod("getInstance").invoke(null), api.getMethod("isShaderPackInUse"),
                worldRendererPipeline);
    }

    @Override
    public boolean canSwap() {
        try {
            if (!(boolean) bindings.isShaderPackInUse().invoke(bindings.irisApi())) return true;
            Object manager = bindings.getPipelineManager().invoke(null);
            if (manager == null) return false;
            Object key = bindings.getCurrentDimension().invoke(null);
            if (key == null) return false;
            return pipelines(manager).get(key) != null;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Override
    public boolean shaderPackInUse() {
        try {
            return (boolean) bindings.isShaderPackInUse().invoke(bindings.irisApi());
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    @Override
    public String describe() {
        StringBuilder out = new StringBuilder();
        try {
            Object manager = bindings.getPipelineManager().invoke(null);
            Object active = manager == null ? null
                    : manager.getClass().getMethod("getPipelineNullable").invoke(manager);
            out.append("pipeline=").append(active == null ? "null" : active.getClass().getName());
            Object key = bindings.getCurrentDimension().invoke(null);
            Object mapped = manager == null || key == null ? null : pipelines(manager).get(key);
            out.append(" mapped=").append(mapped == null ? "null" : mapped.getClass().getSimpleName());
            out.append(" packInUse=").append(bindings.isShaderPackInUse().invoke(bindings.irisApi()));
            out.append(" shadowPass=").append(bindings.irisApi().getClass()
                    .getMethod("isRenderingShadowPass").invoke(bindings.irisApi()));
            Object renderer = net.minecraft.client.MinecraftClient.getInstance().worldRenderer;
            Object field = renderer == null ? null : bindings.worldRendererPipeline().get(renderer);
            out.append(" worldRendererPipeline=")
                    .append(field == null ? "null" : field.getClass().getSimpleName());
            if (active != null) {
                out.append(" override=").append(active.getClass()
                        .getMethod("shouldOverrideShaders").invoke(active));
                out.append(" mainBound=").append(readField(active, "isMainBound"));
                out.append(" renderingWorld=").append(readField(active, "isRenderingWorld"));
                out.append(" phase=").append(readField(active, "phase"));
            }
        } catch (Throwable t) {
            out.append(" describeFailed=").append(t);
        }
        return out.toString();
    }

    private static Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            return "?";
        }
    }

    @Override
    public void begin() {
        installPipelineField();
        if (savedPipeline != null) return;
        try {
            if (!(boolean) bindings.isShaderPackInUse().invoke(bindings.irisApi())) return;
            Object manager = bindings.getPipelineManager().invoke(null);
            if (manager == null) return;
            Object key = bindings.getCurrentDimension().invoke(null);
            if (key == null) return;

            Map<Object, Object> pipelines = pipelines(manager);
            Object active = pipelines.get(key);
            if (active == null || bindings.vanillaClass().isInstance(active)) return;

            if (vanillaPipeline == null) vanillaPipeline = bindings.vanillaConstructor().newInstance();
            swappedKey = key;
            savedPipeline = active;
            pipelines.put(key, vanillaPipeline);
            bindings.preparePipeline().invoke(manager, key);
        } catch (ReflectiveOperationException e) {
            end();
            throw new IllegalStateException(e);
        }
    }

    private void installPipelineField() {
        if (pipelineFieldInstalled) return;
        try {
            net.minecraft.client.render.WorldRenderer renderer =
                    net.minecraft.client.MinecraftClient.getInstance().worldRenderer;
            if (renderer == null) return;
            if (bindings.worldRendererPipeline().get(renderer) != null) return;
            if (vanillaPipeline == null) vanillaPipeline = bindings.vanillaConstructor().newInstance();
            bindings.worldRendererPipeline().set(renderer, vanillaPipeline);
            pipelineFieldInstalled = true;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private void clearPipelineField() {
        if (!pipelineFieldInstalled) return;
        pipelineFieldInstalled = false;
        try {
            net.minecraft.client.render.WorldRenderer renderer =
                    net.minecraft.client.MinecraftClient.getInstance().worldRenderer;
            if (renderer != null) bindings.worldRendererPipeline().set(renderer, null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void end() {
        clearPipelineField();
        if (savedPipeline == null) return;
        Object key = swappedKey;
        Object saved = savedPipeline;
        savedPipeline = null;
        swappedKey = null;
        try {
            Object manager = bindings.getPipelineManager().invoke(null);
            if (manager == null) return;
            pipelines(manager).put(key, saved);
            bindings.preparePipeline().invoke(manager, key);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Object, Object> pipelines(Object manager) throws IllegalAccessException {
        return (Map<Object, Object>) bindings.pipelinesPerDimension().get(manager);
    }
}
