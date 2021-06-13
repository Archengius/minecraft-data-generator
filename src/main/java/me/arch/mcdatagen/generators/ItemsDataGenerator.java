package me.arch.mcdatagen.generators;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.arch.mcdatagen.util.DataGeneratorUtils;
import net.minecraft.enchantment.EnchantmentTarget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ItemsDataGenerator implements IDataGenerator {

    private static List<Item> calculateItemsToRepairWith(Registry<Item> itemRegistry, Item sourceItem) {
        ItemStack sourceItemStack = sourceItem.getDefaultStack();
        return itemRegistry.stream()
                .filter(otherItem -> sourceItem.canRepair(sourceItemStack, otherItem.getDefaultStack()))
                .collect(Collectors.toList());
    }

    private static List<EnchantmentTarget> getApplicableEnchantmentTargets(Item sourceItem) {
        return Arrays.stream(EnchantmentTarget.values())
                .filter(target -> target.isAcceptableItem(sourceItem))
                .collect(Collectors.toList());
    }

    @Override
    public String getDataName() {
        return "items";
    }

    @Override
    public JsonArray generateDataJson() {
        JsonArray resultArray = new JsonArray();
        Registry<Item> itemRegistry = Registry.ITEM;
        itemRegistry.stream().forEach(item -> resultArray.add(generateItem(itemRegistry, item)));
        return resultArray;
    }

    public static JsonObject generateItem(Registry<Item> itemRegistry, Item item) {
        JsonObject itemDesc = new JsonObject();
        Identifier registryKey = itemRegistry.getKey(item).orElseThrow().getValue();

        itemDesc.addProperty("id", itemRegistry.getRawId(item));
        itemDesc.addProperty("name", registryKey.getPath());

        itemDesc.addProperty("displayName", DataGeneratorUtils.translateText(item.getTranslationKey()));
        itemDesc.addProperty("stackSize", item.getMaxCount());

        List<EnchantmentTarget> enchantmentTargets = getApplicableEnchantmentTargets(item);

        JsonArray enchantCategoriesArray = new JsonArray();
        for (EnchantmentTarget target : enchantmentTargets) {
            enchantCategoriesArray.add(EnchantmentsDataGenerator.getEnchantmentTargetName(target));
        }
        itemDesc.add("enchantCategories", enchantCategoriesArray);

        if (item.isDamageable()) {
            List<Item> repairWithItems = calculateItemsToRepairWith(itemRegistry, item);

            JsonArray fixedWithArray = new JsonArray();
            for (Item repairWithItem : repairWithItems) {
                Identifier repairWithName = itemRegistry.getKey(repairWithItem).orElseThrow().getValue();
                fixedWithArray.add(repairWithName.getPath());
            }
            itemDesc.add("fixedWith", fixedWithArray);

            int maxDurability = item.getMaxDamage();
            itemDesc.addProperty("durability", maxDurability);
            itemDesc.addProperty("maxDurability", maxDurability);
        }
        return itemDesc;
    }
}
