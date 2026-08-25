package kombuchamc.createsecurity.recipe;

import com.google.gson.JsonObject;
import kombuchamc.createsecurity.items.KeycardItem;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.recipe.Ingredient;
import net.minecraft.recipe.RecipeSerializer;
import net.minecraft.recipe.ShapelessRecipe;
import net.minecraft.recipe.book.CraftingRecipeCategory;
import net.minecraft.registry.DynamicRegistryManager;
import net.minecraft.util.Identifier;
import net.minecraft.util.collection.DefaultedList;

public class KeycardRecipe extends ShapelessRecipe {

    public KeycardRecipe(Identifier id, String group, CraftingRecipeCategory category,
                         ItemStack output, DefaultedList<Ingredient> input) {
        super(id, group, category, output, input);
    }

    @Override
    public ItemStack craft(net.minecraft.inventory.RecipeInputInventory inventory,
                           DynamicRegistryManager registries) {
        ItemStack result = super.craft(inventory, registries);
        for (int i = 0; i < inventory.size(); i++) {
            if (inventory.getStack(i).getItem() instanceof DyeItem dye) {
                KeycardItem.setColor(result, dye.getColor());
                break;
            }
        }
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.KEYCARD;
    }

    public static class Serializer implements RecipeSerializer<KeycardRecipe> {

        private final ShapelessRecipe.Serializer delegate = new ShapelessRecipe.Serializer();

        @Override
        public KeycardRecipe read(Identifier id, JsonObject json) {
            return convert(delegate.read(id, json));
        }

        @Override
        public KeycardRecipe read(Identifier id, PacketByteBuf buf) {
            return convert(delegate.read(id, buf));
        }

        @Override
        public void write(PacketByteBuf buf, KeycardRecipe recipe) {
            delegate.write(buf, recipe);
        }

        private static KeycardRecipe convert(ShapelessRecipe recipe) {
            return new KeycardRecipe(recipe.getId(), recipe.getGroup(), recipe.getCategory(),
                    recipe.getOutput(DynamicRegistryManager.EMPTY), recipe.getIngredients());
        }
    }
}
