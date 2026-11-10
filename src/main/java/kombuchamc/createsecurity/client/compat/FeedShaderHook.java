package kombuchamc.createsecurity.client.compat;

public interface FeedShaderHook {

    void begin();

    void end();

    boolean canSwap();

    boolean shaderPackInUse();

    String describe();
}
