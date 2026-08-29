package dev.yuzhe.aeaffinity.gametest;

import appeng.api.config.Actionable;
import appeng.api.parts.PartHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.util.AEColor;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.core.definitions.AEParts;
import appeng.me.cells.BasicCellInventory;
import appeng.me.helpers.BaseActionSource;
import appeng.blockentity.storage.DriveBlockEntity;
import dev.yuzhe.aeaffinity.config.ActivationMode;
import dev.yuzhe.aeaffinity.config.AeAffinityConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

@GameTestHolder("aeaffinity")
@PrefixGameTestTemplate(false)
public final class CreateVaultGameTests {
    private static final BlockPos ENERGY = new BlockPos(1, 2, 2);
    private static final BlockPos DRIVE = new BlockPos(2, 2, 2);
    private static final BlockPos BUS = new BlockPos(3, 2, 2);
    private static final BlockPos VAULT_A = new BlockPos(4, 2, 2);
    private static final BlockPos VAULT_B = new BlockPos(5, 2, 2);
    private static final ResourceLocation VAULT_ID = ResourceLocation.fromNamespaceAndPath("create", "item_vault");

    private CreateVaultGameTests() {
    }

    @GameTest(template = "empty", timeoutTicks = 120, batch = "create_oracle")
    public static void vaultMembersExposeOneSharedInventory(GameTestHelper helper) {
        setup(helper, AEItemKey.of(Items.COBBLESTONE), 0, 20, ActivationMode.OFF);
        helper.runAfterDelay(40, () -> {
            var first = vaultHandler(helper, VAULT_A, Direction.WEST);
            var second = vaultHandler(helper, VAULT_B, Direction.EAST);
            helper.assertTrue(first == second, "Vault members did not resolve to one shared handler");
            insert(first, Items.COBBLESTONE, 64, helper);
            helper.assertTrue(count(second, Items.COBBLESTONE) == 64, "Vault members did not share contents");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "create_sparse")
    public static void sparseItemMovesFromCellToVault(GameTestHelper helper) {
        var helmet = AEItemKey.of(Items.IRON_HELMET);
        var fixture = setup(helper, helmet, 1, 20, ActivationMode.ALL);
        helper.succeedWhen(() -> {
            var cell = fixture.drive().getCellInventory(0);
            helper.assertTrue(cell != null, "drive did not mount item cell");
            long inVault = count(vaultHandler(helper, VAULT_A, Direction.WEST), Items.IRON_HELMET);
            helper.assertTrue(inVault == 1, "helmet did not reach Create Vault");
            helper.assertTrue(cell.getAvailableStacks().get(helmet) == 0, "helmet remained in cell");
            helper.assertTrue(inVault + cell.getAvailableStacks().get(helmet) == 1, "helmet count was not conserved");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "create_bulk")
    public static void bulkMovesFromVaultToCell(GameTestHelper helper) {
        var cobble = AEItemKey.of(Items.COBBLESTONE);
        var fixture = setup(helper, cobble, 0, 20, ActivationMode.ALL);
        helper.runAfterDelay(40, () -> insert(vaultHandler(helper, VAULT_A, Direction.WEST), Items.COBBLESTONE, 256, helper));
        helper.succeedWhen(() -> {
            var cell = fixture.drive().getCellInventory(0);
            helper.assertTrue(cell != null, "drive did not mount item cell");
            long inVault = count(vaultHandler(helper, VAULT_A, Direction.WEST), Items.COBBLESTONE);
            helper.assertTrue(cell.getAvailableStacks().get(cobble) == 256, "bulk items did not reach cell");
            helper.assertTrue(inVault == 0, "bulk items remained in Create Vault");
            helper.assertTrue(inVault + cell.getAvailableStacks().get(cobble) == 256, "bulk count was not conserved");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 550, batch = "create_external")
    public static void directVaultMutationWakesBackedOffGrid(GameTestHelper helper) {
        var cobble = AEItemKey.of(Items.COBBLESTONE);
        var fixture = setup(helper, cobble, 0, 10_000, ActivationMode.ALL);
        helper.runAfterDelay(350, () -> insert(vaultHandler(helper, VAULT_A, Direction.WEST), Items.COBBLESTONE, 256, helper));
        helper.succeedWhen(() -> {
            var cell = fixture.drive().getCellInventory(0);
            helper.assertTrue(cell != null, "drive did not mount item cell");
            helper.assertTrue(cell.getAvailableStacks().get(cobble) == 256,
                    "direct Vault mutation did not wake affinity before the backed-off round");
            helper.assertTrue(count(vaultHandler(helper, VAULT_A, Direction.WEST), Items.COBBLESTONE) == 0,
                    "bulk items remained in externally changed Vault");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "create_full")
    public static void fullVaultDoesNotExtractCell(GameTestHelper helper) {
        var helmet = AEItemKey.of(Items.IRON_HELMET);
        var fixture = setup(helper, helmet, 1, 20, ActivationMode.OFF, false);
        helper.runAfterDelay(100, () -> {
            var vault = vaultHandler(helper, VAULT_A, Direction.WEST);
            for (int slot = 0; slot < vault.getSlots(); slot++) {
                var filler = new ItemStack(Items.WOODEN_SWORD);
                filler.setDamageValue(slot + 1);
                var remainder = vault.insertItem(slot, filler, false);
                helper.assertTrue(remainder.isEmpty(), "failed to fill Vault slot " + slot);
            }
            var probe = new ItemStack(Items.IRON_HELMET);
            for (int slot = 0; slot < vault.getSlots(); slot++) {
                probe = vault.insertItem(slot, probe, true);
            }
            helper.assertTrue(!probe.isEmpty(), "Vault was not actually full");
            AeAffinityConfig.ACTIVATION.set(ActivationMode.ALL);
        });
        helper.runAfterDelay(300, () -> {
            var cell = fixture.drive().getCellInventory(0);
            long cellAmount = cell == null ? -1 : cell.getAvailableStacks().get(helmet);
            var vault = vaultHandler(helper, VAULT_A, Direction.WEST);
            long vaultHelmets = count(vault, Items.IRON_HELMET);
            helper.assertTrue(cellAmount == 1 && vaultHelmets == 0,
                    "full Vault changed helmet placement: cell=" + cellAmount + ", vault=" + vaultHelmets
                            + ", fillerVault=" + count(vault, Items.WOODEN_SWORD));
            helper.succeed();
        });
    }

    @GameTest(template = "empty", timeoutTicks = 400, batch = "create_destroyed")
    public static void destroyedVaultCancelsMigration(GameTestHelper helper) {
        var helmet = AEItemKey.of(Items.IRON_HELMET);
        var fixture = setup(helper, helmet, 1, 20, ActivationMode.ALL);
        helper.destroyBlock(VAULT_A);
        helper.runAfterDelay(300, () -> {
            var cell = fixture.drive().getCellInventory(0);
            helper.assertTrue(cell != null && cell.getAvailableStacks().get(helmet) == 1,
                    "destroyed Vault did not cancel migration");
            helper.succeed();
        });
    }

    private static Fixture setup(
            GameTestHelper helper,
            AEItemKey cellKey,
            long cellAmount,
            int maxIdleTicks,
            ActivationMode activation) {
        return setup(helper, cellKey, cellAmount, maxIdleTicks, activation, true);
    }

    private static Fixture setup(
            GameTestHelper helper,
            AEItemKey cellKey,
            long cellAmount,
            int maxIdleTicks,
            ActivationMode activation,
            boolean secondVaultBlock) {
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(VAULT_ID), "Create Item Vault is not loaded");
        AeAffinityConfig.ACTIVATION.set(activation);
        AeAffinityConfig.CHARGE_ENERGY.set(true);
        AeAffinityConfig.MIN_IDLE_TICKS.set(20);
        AeAffinityConfig.MAX_IDLE_TICKS.set(maxIdleTicks);
        AeAffinityConfig.PLANNING_TICKS.set(1);

        helper.setBlock(ENERGY, AEBlocks.CREATIVE_ENERGY_CELL.block());
        helper.setBlock(DRIVE, AEBlocks.DRIVE.block());
        var vault = BuiltInRegistries.BLOCK.get(VAULT_ID);
        helper.setBlock(VAULT_A, vault);
        if (secondVaultBlock) {
            helper.setBlock(VAULT_B, vault);
        }

        var player = helper.makeMockPlayer(GameType.CREATIVE);
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(BUS), Direction.UP, player,
                AEParts.GLASS_CABLE.item(AEColor.TRANSPARENT));
        PartHelper.setPart(helper.getLevel(), helper.absolutePos(BUS), Direction.EAST, player,
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
        return new Fixture(drive);
    }

    private static IItemHandler vaultHandler(GameTestHelper helper, BlockPos pos, Direction context) {
        var handler = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, helper.absolutePos(pos), context);
        helper.assertTrue(handler != null, "Create Vault did not expose an item handler at " + pos.toShortString());
        return handler;
    }

    private static void insert(IItemHandler inventory, Item item, int amount, GameTestHelper helper) {
        int remaining = amount;
        for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
            var remainder = inventory.insertItem(slot, new ItemStack(item, remaining), false);
            remaining = remainder.getCount();
        }
        helper.assertTrue(remaining == 0, "Vault rejected " + remaining + " items");
    }

    private static long count(IItemHandler inventory, Item item) {
        long count = 0;
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            var stack = inventory.getStackInSlot(slot);
            if (stack.is(item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private record Fixture(DriveBlockEntity drive) {
    }
}
