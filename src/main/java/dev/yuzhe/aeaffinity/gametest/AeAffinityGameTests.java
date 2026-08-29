package dev.yuzhe.aeaffinity.gametest;

import appeng.api.config.Actionable;
import appeng.api.config.AccessRestriction;
import appeng.api.config.Settings;
import appeng.api.networking.GridHelper;
import appeng.api.networking.IGridNode;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.me.cells.BasicCellInventory;
import appeng.me.helpers.BaseActionSource;
import appeng.blockentity.storage.DriveBlockEntity;
import appeng.api.util.AEColor;
import appeng.parts.storagebus.StorageBusPart;
import dev.yuzhe.aeaffinity.config.ActivationMode;
import dev.yuzhe.aeaffinity.config.AeAffinityConfig;
import dev.yuzhe.aeaffinity.grid.IAffinityGridService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("aeaffinity")
@PrefixGameTestTemplate(false)
public final class AeAffinityGameTests {
    private static final BlockPos ENERGY = new BlockPos(1, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(2, 2, 2);
    private static final BlockPos BUS = new BlockPos(3, 2, 2);
    private static final BlockPos CHEST = new BlockPos(4, 2, 2);
    private static final BlockPos ANCHOR = new BlockPos(2, 2, 3);

    private AeAffinityGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void sparseUnstackableMovesFromCellToChest(GameTestHelper helper) {
        var helmet = AEItemKey.of(Items.IRON_HELMET);
        var fixture = setupNetwork(helper, helmet, 1);

        helper.succeedWhen(() -> {
            var mountedCell = fixture.drive().getCellInventory(0);
            helper.assertTrue(mountedCell != null, "drive did not mount item cell");
            helper.assertTrue(count(fixture.chest(), Items.IRON_HELMET) == 1, "helmet has not reached chest");
            helper.assertTrue(mountedCell.getAvailableStacks().get(helmet) == 0, "helmet still present in cell");
            helper.assertTrue(count(fixture.chest(), Items.IRON_HELMET) + mountedCell.getAvailableStacks().get(helmet) == 1,
                    "helmet count was not conserved");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void bulkMovesFromChestToCell(GameTestHelper helper) {
        var cobblestone = AEItemKey.of(Items.COBBLESTONE);
        var fixture = setupNetwork(helper, cobblestone, 0);
        for (int slot = 0; slot < 4; slot++) {
            fixture.chest().setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
        }

        helper.succeedWhen(() -> {
            var mountedCell = fixture.drive().getCellInventory(0);
            helper.assertTrue(mountedCell != null, "drive did not mount item cell");
            helper.assertTrue(mountedCell.getAvailableStacks().get(cobblestone) == 256,
                    "bulk stack has not reached cell");
            helper.assertTrue(count(fixture.chest(), Items.COBBLESTONE) == 0, "bulk stack still present in chest");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void fullTargetDoesNotExtractSource(GameTestHelper helper) {
        var helmet = AEItemKey.of(Items.IRON_HELMET);
        var fixture = setupNetwork(helper, helmet, 1);
        for (int slot = 0; slot < fixture.chest().getContainerSize(); slot++) {
            fixture.chest().setItem(slot, new ItemStack(Items.DIRT));
        }
        assertUnmovedAfter(helper, fixture, helmet, 300);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void removedEndpointCancelsCandidate(GameTestHelper helper) {
        var helmet = AEItemKey.of(Items.IRON_HELMET);
        var fixture = setupNetwork(helper, helmet, 1);
        helper.destroyBlock(BUS);
        assertUnmovedAfter(helper, fixture, helmet, 300);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void voidingTargetIsExcluded(GameTestHelper helper) {
        var helmet = AEItemKey.of(Items.IRON_HELMET);
        var fixture = setupNetwork(helper, helmet, 1);
        fixture.bus().getUpgrades().addItems(AEItems.VOID_CARD.stack());
        assertUnmovedAfter(helper, fixture, helmet, 300);
    }

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void extractOnlyBusIsNotATarget(GameTestHelper helper) {
        var helmet = AEItemKey.of(Items.IRON_HELMET);
        var fixture = setupNetwork(helper, helmet, 1);
        fixture.bus().getConfigManager().putSetting(Settings.ACCESS, AccessRestriction.READ);
        assertUnmovedAfter(helper, fixture, helmet, 300);
    }

    @GameTest(template = "empty", timeoutTicks = 100)
    public static void breakingAnchorNodeClearsActivation(GameTestHelper helper) {
        setupNetwork(helper, AEItemKey.of(Items.IRON_HELMET), 0);
        PartHelper.setPart(
                helper.getLevel(), helper.absolutePos(ANCHOR), Direction.UP,
                helper.makeMockPlayer(GameType.CREATIVE),
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));

        helper.runAfterDelay(10, () -> {
            var node = findNode(helper, ANCHOR);
            var service = node.getGrid().getService(IAffinityGridService.class);
            service.addAnchor(node);
            helper.assertTrue(service.hasAnchor(), "anchor was not registered");
            helper.destroyBlock(ANCHOR);
        });
        helper.runAfterDelay(30, () -> {
            var service = findNode(helper, DRIVE).getGrid().getService(IAffinityGridService.class);
            helper.assertTrue(!service.hasAnchor(), "destroyed node left a dangling anchor");
            helper.succeed();
        });
    }

    private static IGridNode findNode(GameTestHelper helper, BlockPos pos) {
        var host = GridHelper.getNodeHost(helper.getLevel(), helper.absolutePos(pos));
        helper.assertTrue(host != null, "no AE node host at " + pos.toShortString());
        for (var side : Direction.values()) {
            var node = host.getGridNode(side);
            if (node != null) {
                return node;
            }
        }
        var node = host.getGridNode(null);
        helper.assertTrue(node != null, "no AE grid node at " + pos.toShortString());
        return node;
    }

    private static void assertUnmovedAfter(
            GameTestHelper helper,
            Fixture fixture,
            AEItemKey key,
            int ticks) {
        helper.runAfterDelay(ticks, () -> {
            var mountedCell = fixture.drive().getCellInventory(0);
            helper.assertTrue(mountedCell != null, "drive did not mount item cell");
            helper.assertTrue(mountedCell.getAvailableStacks().get(key) == 1, "source item was extracted");
            helper.assertTrue(count(fixture.chest(), key.getItem()) == 0, "source item reached excluded target");
            helper.succeed();
        });
    }

    private static Fixture setupNetwork(GameTestHelper helper, AEItemKey cellKey, long cellAmount) {
        AeAffinityConfig.ACTIVATION.set(ActivationMode.ALL);
        AeAffinityConfig.MIN_IDLE_TICKS.set(20);
        AeAffinityConfig.MAX_IDLE_TICKS.set(20);
        AeAffinityConfig.PLANNING_TICKS.set(1);

        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(DRIVE, AEBlocks.DRIVE.block());
        helper.setBlock(CHEST, Blocks.CHEST);
        var player = helper.makeMockPlayer(GameType.CREATIVE);
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(BUS), Direction.UP, player,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        var bus = PartHelper.setPart(helper.getLevel(), helper.absolutePos(BUS), Direction.EAST, player,
                AEParts.STORAGE_BUS.asItem());

        var cellStack = AEItems.ITEM_CELL_1K.stack();
        var cell = BasicCellInventory.createInventory(cellStack, null);
        if (cellAmount > 0) {
            helper.assertTrue(
                    cell.insert(cellKey, cellAmount, Actionable.MODULATE, new BaseActionSource()) == cellAmount,
                    "failed to seed item cell");
        }

        DriveBlockEntity drive = helper.getBlockEntity(DRIVE);
        helper.assertTrue(drive.getInternalInventory().addItems(cellStack).isEmpty(), "failed to install item cell");
        ChestBlockEntity chest = helper.getBlockEntity(CHEST);
        return new Fixture(drive, chest, bus);
    }

    private static long count(ChestBlockEntity chest, net.minecraft.world.item.Item item) {
        long count = 0;
        for (int slot = 0; slot < chest.getContainerSize(); slot++) {
            ItemStack stack = chest.getItem(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private record Fixture(DriveBlockEntity drive, ChestBlockEntity chest, StorageBusPart bus) {
    }
}
