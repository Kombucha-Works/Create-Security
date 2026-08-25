package kombuchamc.createsecurity.recipe;

import kombuchamc.createsecurity.CreateSecurity;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;

public class ModRecipes {

    public static RecipeSerializer<KeycardRecipe> KEYCARD;

    public static void registerRecipes() {
        KEYCARD = Registry.register(Registries.RECIPE_SERIALIZER,
                new Identifier(CreateSecurity.MOD_ID, "keycard"), new KeycardRecipe.Serializer());
    }
}
