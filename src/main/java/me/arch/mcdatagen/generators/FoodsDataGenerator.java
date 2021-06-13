package me.arch.mcdatagen.generators;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.arch.mcdatagen.util.DataGeneratorUtils;
import net.minecraft.item.FoodComponent;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.Objects;

public class FoodsDataGenerator implements IDataGenerator {

    @Override
    public String getDataName() {
        return "foods";
    }

    public JsonArray generateDataJson() {
        JsonArray resultsArray = new JsonArray();
        Registry<Item> itemRegistry = Registry.ITEM;
        itemRegistry.stream()
                .filter(Item::isFood)
                .forEach(food -> resultsArray.add(generateFoodDescriptor(itemRegistry, food)));
        return resultsArray;
    }

    public static JsonObject generateFoodDescriptor(Registry<Item> registry, Item foodItem) {
        JsonObject foodDesc = new JsonObject();
        Identifier registryKey = registry.getKey(foodItem).orElseThrow().getValue();

        foodDesc.addProperty("id", registry.getRawId(foodItem));
        foodDesc.addProperty("name", registryKey.getPath());

        foodDesc.addProperty("stackSize", foodItem.getMaxCount());
        foodDesc.addProperty("displayName", DataGeneratorUtils.translateText(foodItem.getTranslationKey()));

        FoodComponent foodComponent = Objects.requireNonNull(foodItem.getFoodComponent());
        float foodPoints = foodComponent.getHunger();
        float saturationRatio = foodComponent.getSaturationModifier() * 2.0F;
        float saturation = foodPoints * saturationRatio;

        foodDesc.addProperty("foodPoints", foodPoints);
        foodDesc.addProperty("saturation", saturation);

        foodDesc.addProperty("effectiveQuality", foodPoints + saturation);
        foodDesc.addProperty("saturationRatio", saturationRatio);
        return foodDesc;
    }
}
