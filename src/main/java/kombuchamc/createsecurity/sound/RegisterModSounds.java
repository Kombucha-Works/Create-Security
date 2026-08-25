package kombuchamc.createsecurity.sound;

import kombuchamc.createsecurity.CreateSecurity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

public class RegisterModSounds {

    public static final SoundEvent ALARM = registerSoundEvent("alarm");

    private static SoundEvent registerSoundEvent(String name) {
        Identifier id = new Identifier(CreateSecurity.MOD_ID, name);
        return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
    }

    public static void registerModSounds() {

    }
}
