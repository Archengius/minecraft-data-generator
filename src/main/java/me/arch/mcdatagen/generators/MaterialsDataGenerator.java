package me.arch.mcdatagen.generators;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import me.arch.mcdatagen.mixin.MiningToolItemAccessor;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.Material;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.item.MiningToolItem;
import net.minecraft.item.SwordItem;
import net.minecraft.tag.BlockTags;
import net.minecraft.tag.Tag;
import net.minecraft.util.registry.Registry;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

//TODO entire idea of linking materials to tool speeds is obsolete and just wrong now,
//TODO but we kinda have to support it to let old code work for computing digging times,
//TODO so for now we will handle materials as "virtual" ones based on which tools can break blocks
public class MaterialsDataGenerator implements IDataGenerator {

    private static ImmutableMap<String, ImmutableList<String>> COMPOSITE_MATERIALS = ImmutableMap.<String, ImmutableList<String>>builder()
            .put("plant_mineable_by_axe", ImmutableList.of(
                    "plant",
                    makeMaterialNameForTag(BlockTags.AXE_MINEABLE)))

            .put("gourd_mineable_by_axe", ImmutableList.of(
                    "gourd",
                    makeMaterialNameForTag(BlockTags.AXE_MINEABLE)))

            .put("leaves_mineable_by_hoe", ImmutableList.of(
                    makeMaterialNameForTag(BlockTags.LEAVES),
                    makeMaterialNameForTag(BlockTags.HOE_MINEABLE)))

            .put("leaves_mineable_by_axe_and_hoe", ImmutableList.of(
                    makeMaterialNameForTag(BlockTags.LEAVES),
                    makeMaterialNameForTag(BlockTags.AXE_MINEABLE),
                    makeMaterialNameForTag(BlockTags.HOE_MINEABLE)))

            .put("vine_plant_mineable_by_axe", ImmutableList.of(
                    "vine_or_glow_lichen",
                    "plant",
                    makeMaterialNameForTag(BlockTags.AXE_MINEABLE)
            )).build();

    @Override
    public String getDataName() {
        return "materials";
    }

    private static String makeMaterialNameForTag(Tag<Block> tag) {
        Tag.Identified<Block> identifiedTag = (Tag.Identified<Block>) tag;
        return identifiedTag.getId().getPath();
    }

    public static class MaterialInfo {
        private final String materialName;
        private final Predicate<BlockState> predicate;
        private boolean trumpsAllOtherMaterials;

        public MaterialInfo(String materialName, Predicate<BlockState> predicate) {
            this.materialName = materialName;
            this.predicate = predicate;
        }

        protected MaterialInfo setTrumpsAllOtherMaterials() {
            this.trumpsAllOtherMaterials = true;
            return this;
        }

        public String getMaterialName() {
            return materialName;
        }

        public Predicate<BlockState> getPredicate() {
            return predicate;
        }

        public boolean trumpsAllOtherMaterials() {
            return trumpsAllOtherMaterials;
        }

        @Override
        public String toString() {
            return materialName;
        }
    }

    private static void createCompositeMaterialInfo(List<MaterialInfo> allMaterials, String compositeMaterialName, List<String> combinedMaterials) {
        List<MaterialInfo> mappedMaterials = combinedMaterials.stream()
                .map(otherName -> allMaterials.stream()
                        .filter(other -> other.getMaterialName().equals(otherName))
                        .findFirst().orElseThrow(() -> new RuntimeException("Material not found with name " + otherName)))
                .collect(Collectors.toList());

        Predicate<BlockState> compositePredicate = blockState ->
                mappedMaterials.stream().allMatch(it -> it.getPredicate().test(blockState));

        MaterialInfo materialInfo = new MaterialInfo(compositeMaterialName, compositePredicate)
                .setTrumpsAllOtherMaterials();
        allMaterials.add(0, materialInfo);
    }

    private static void createCompositeMaterial(Map<String, Map<Item, Float>> allMaterials, String compositeMaterialName, List<String> combinedMaterials) {
        Map<Item, Float> resultingToolSpeeds = new HashMap<>();
        combinedMaterials.stream()
                .map(allMaterials::get)
                .forEach(resultingToolSpeeds::putAll);
        allMaterials.put(compositeMaterialName, resultingToolSpeeds);
    }

    public static List<MaterialInfo> getGlobalMaterialInfo() {
        ArrayList<MaterialInfo> resultList = new ArrayList<>();

        resultList.add(new MaterialInfo("vine_or_glow_lichen", blockState -> blockState.isOf(Blocks.VINE) || blockState.isOf(Blocks.GLOW_LICHEN)));
        resultList.add(new MaterialInfo("coweb", blockState -> blockState.isOf(Blocks.COBWEB)));

        resultList.add(new MaterialInfo("leaves", blockState -> blockState.isIn(BlockTags.LEAVES)));
        resultList.add(new MaterialInfo("wool", blockState -> blockState.isIn(BlockTags.WOOL)));

        resultList.add(new MaterialInfo("gourd", blockState -> blockState.getMaterial() == Material.GOURD));
        resultList.add(new MaterialInfo("plant", blockState -> blockState.getMaterial() == Material.PLANT || blockState.getMaterial() == Material.REPLACEABLE_PLANT));

        HashSet<String> uniqueMaterialNames = new HashSet<>();

        Registry<Item> itemRegistry = Registry.ITEM;
        itemRegistry.forEach(item -> {
            if (item instanceof MiningToolItem toolItem) {
                Tag<Block> effectiveBlocks = ((MiningToolItemAccessor) toolItem).getEffectiveBlocks();
                String materialName = makeMaterialNameForTag(effectiveBlocks);

                if (!uniqueMaterialNames.contains(materialName)) {
                    uniqueMaterialNames.add(materialName);
                    resultList.add(new MaterialInfo(materialName, blockState -> blockState.isIn(effectiveBlocks)));
                }
            }
        });

        COMPOSITE_MATERIALS.forEach((name, values) -> createCompositeMaterialInfo(resultList, name, values));
        return resultList;
    }

    @Override
    public JsonElement generateDataJson() {
        Registry<Item> itemRegistry = Registry.ITEM;

        Map<String, Map<Item, Float>> materialMiningSpeeds = new HashMap<>();
        materialMiningSpeeds.put("default", ImmutableMap.of());

        //Special materials used for shears and swords special mining speed logic
        Map<Item, Float> leavesMaterialSpeeds = new HashMap<>();
        Map<Item, Float> cowebMaterialSpeeds = new HashMap<>();
        Map<Item, Float> plantMaterialSpeeds = new HashMap<>();
        Map<Item, Float> gourdMaterialSpeeds = new HashMap<>();

        materialMiningSpeeds.put(makeMaterialNameForTag(BlockTags.LEAVES), leavesMaterialSpeeds);
        materialMiningSpeeds.put("coweb", cowebMaterialSpeeds);
        materialMiningSpeeds.put("plant", plantMaterialSpeeds);
        materialMiningSpeeds.put("gourd", gourdMaterialSpeeds);

        //Shears need special handling because they do not follow normal rules like tools
        leavesMaterialSpeeds.put(Items.SHEARS, 15.0f);
        cowebMaterialSpeeds.put(Items.SHEARS, 15.0f);
        materialMiningSpeeds.put("vine_or_glow_lichen", ImmutableMap.of(Items.SHEARS, 2.0f));
        materialMiningSpeeds.put("wool", ImmutableMap.of(Items.SHEARS, 5.0f));

        itemRegistry.forEach(item -> {
            //Tools are handled rather easily and do not require anything else
            if (item instanceof MiningToolItem toolItem) {
                Tag<Block> effectiveBlocks = ((MiningToolItemAccessor) toolItem).getEffectiveBlocks();
                String materialName = makeMaterialNameForTag(effectiveBlocks);

                Map<Item, Float> materialSpeeds = materialMiningSpeeds.computeIfAbsent(materialName, k -> new HashMap<>());
                float miningSpeed = ((MiningToolItemAccessor) toolItem).getMiningSpeed();
                materialSpeeds.put(item, miningSpeed);
            }

            //Swords require special treatment
            if (item instanceof SwordItem) {
                cowebMaterialSpeeds.put(item, 15.0f);
                plantMaterialSpeeds.put(item, 1.5f);
                leavesMaterialSpeeds.put(item, 1.5f);
                gourdMaterialSpeeds.put(item, 1.5f);
            }
        });

        COMPOSITE_MATERIALS.forEach((name, values) -> createCompositeMaterial(materialMiningSpeeds, name, values));

        JsonObject resultObject = new JsonObject();

        for (var entry : materialMiningSpeeds.entrySet()) {
            JsonObject toolSpeedsObject = new JsonObject();

            for (var toolEntry : entry.getValue().entrySet()) {
                int rawItemId = itemRegistry.getRawId(toolEntry.getKey());
                toolSpeedsObject.addProperty(Integer.toString(rawItemId), toolEntry.getValue());
            }
            resultObject.add(entry.getKey(), toolSpeedsObject);
        }

        return resultObject;
    }
}
