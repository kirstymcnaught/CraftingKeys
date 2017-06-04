package de.skate702.craftingkeys.manager;

import de.skate702.craftingkeys.CraftingKeys;
import de.skate702.craftingkeys.config.Config;
import de.skate702.craftingkeys.util.InputUtil;
import de.skate702.craftingkeys.util.Logger;
import de.skate702.craftingkeys.util.Util;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.init.Blocks;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.lwjgl.input.Keyboard;

/**
 * Provides all needed methods to handle and manage a gui inventory. Does also provide frames for own implementations.
 */
public abstract class ContainerManager {

    /**
     * The Container to work with.
     */
    Container container;

    /**
     * Creates a new ContainerManager with the given container.
     *
     * @param container The container to work with
     */
    ContainerManager(Container container) {
        this.container = container;
    }

    /**
     * Checks the current keyDown-Value and does the work!
     * Use the implementation in ContainerManager if possible. Or define your own.
     */
    public void acceptKey() {

    	// Poll keyboard
    	Config.pollKeyPresses();
    	
        // Get hovered slot and goal slotIndex of pressed key (if pressed)
        Slot currentHoveredSlot = InputUtil.getSlotAtMousePosition((GuiContainer) Util.client.currentScreen);
        int slotIndex = specificKeyToSlotIndex();

        // Handle accepted key
        if (!InputUtil.isSameKey(slotIndex)) {


            if (Config.isKeyDropPressed()) { // DROP

                Logger.info("acceptKey()", "Drop Key pressed.");
                onDropKeyPressed();

            } else if (Config.isKeyInteractPressed()) { // INTERACT

                Logger.info("acceptKey()", "Interaction Key pressed.");
                onInteractionKeyPressed();


            } else if (slotIndex >= 0 && currentHoveredSlot != null &&
                    !Util.isHoldingStack()) { // MOVE FROM SLOT

                Logger.info("acceptKey()", "Key for index " + slotIndex + " pressed.");
                onSpecificKeyPressed(currentHoveredSlot.slotNumber, slotIndex);


            } else if (Util.isHoldingStack()) { // MOVE FROM HAND

                onHolding(slotIndex);
                handleNumKey(currentHoveredSlot);
            } else { // HANDLE NUM KEY MOVING (also with empty hand now -> speed-up)

                handleNumKey(currentHoveredSlot);
            }

        }

    }

    /**
     * Handles what to do when the DropKey is pressed in acceptKey().
     */
    @SuppressWarnings("WeakerAccess")
    void onDropKeyPressed() {

        // Drop every defined dropSlot-Item
        for (int i : getDropSlots()) {
            moveStackToInventory(i);
        }

    }

    /**
     * Handles what to do when the InteractionKey is pressed in acceptKey().
     */
    void onInteractionKeyPressed() {

        // Handles Interaction with items held
        // Stack up on hand if equal or small enough, else throw held stack away
        if (Util.isHoldingStack() && getItemStack(getInteractionSlotIndex()) != null && (
                !Util.getHeldStack().isItemEqual(getItemStack(getInteractionSlotIndex()))
                        || Util.getHeldStack().getCount() + getItemStack(getInteractionSlotIndex()).getCount()
                        > getItemStack(getInteractionSlotIndex()).getMaxStackSize())) {
            moveStackToInventory(-1);
        }

        // Handle Interaction
        if (Config.isKeyStackPressed()) {

            int oldStackSize = -1;
            interact();

            while (Util.isHoldingStack() &&
                    oldStackSize != Util.getHeldStack().getCount()) {

                oldStackSize = Util.getHeldStack().getCount();
                interact();
            }

        } else {
            interact();
        }

        // Finally put what you're holding into a slot.
        moveStackToInventory(-1);
    }

    /**
     * Handles what to do if a specific key is pressed in acceptKey().
     *
     * @param currentHoveredSlot the slot number of the currently hovered Slot (mouse hover)
     * @param slotIndex          the slot index returned from the key input calculation
     */
    void onSpecificKeyPressed(int currentHoveredSlot, int slotIndex) {

        if (Config.isKeyStackPressed()) {
            moveAll(currentHoveredSlot, slotIndex);
            moveStackToInventory(-1);
        } else {
            move(currentHoveredSlot, slotIndex, 1);
        }

    }

    /**
     * Handles what to do with held items in acceptKey().
     *
     * @param slotIndex the slot index returned from the key input calculation
     */
    void onHolding(int slotIndex) {

        onSpecificKeyPressed(-1, slotIndex);

    }

    /**
     * Handles what to do with NumKey-Inputs while holding a item.
     *
     * @param currentHoveredSlot slot where the mouse is right now
     */
    void handleNumKey(Slot currentHoveredSlot) {

        // hotbar-slots are always the last 9 slots of the currently opened inventory
        int hotbarStartIndex = Util.client.player.openContainer.getInventory().size() - 9 - 1;

        if (Util.client.currentScreen instanceof GuiInventory) {
            hotbarStartIndex -= 1;
        }

        int inputdelta;
        KeyBinding[] hotbar = Util.client.gameSettings.keyBindsHotbar;

        if (Keyboard.isKeyDown(hotbar[0].getKeyCode())) {
            inputdelta = 1;
        } else if (Keyboard.isKeyDown(hotbar[1].getKeyCode())) {
            inputdelta = 2;
        } else if (Keyboard.isKeyDown(hotbar[2].getKeyCode())) {
            inputdelta = 3;
        } else if (Keyboard.isKeyDown(hotbar[3].getKeyCode())) {
            inputdelta = 4;
        } else if (Keyboard.isKeyDown(hotbar[4].getKeyCode())) {
            inputdelta = 5;
        } else if (Keyboard.isKeyDown(hotbar[5].getKeyCode())) {
            inputdelta = 6;
        } else if (Keyboard.isKeyDown(hotbar[6].getKeyCode())) {
            inputdelta = 7;
        } else if (Keyboard.isKeyDown(hotbar[7].getKeyCode())) {
            inputdelta = 8;
        } else if (Keyboard.isKeyDown(hotbar[8].getKeyCode())) {
            inputdelta = 9;
        } else {
            return;
        }

        // If no stack is held and a num-key is pressed, get the output by interaction, but only
        // if there could not be meant another stack at mouse position. cool logic!
        if (!Util.isHoldingStack()) {

            if (currentHoveredSlot == null || !currentHoveredSlot.getHasStack()) {
                Logger.info("handleNumKey()", "Trying output to hotbar speedup.");
                onInteractionKeyPressed();
            }
        }

        // If held, move!
        if (Util.isHoldingStack()) {

            leftClick(hotbarStartIndex + inputdelta);
            Logger.info("handleNumKey()", "Moved to hotbar slot " + inputdelta + ".");

            moveStackToInventory(-1);

            // Handle Minecraft handling. Ah...
            while (Keyboard.next()) {
                Logger.info("handleNumKey()", "The cake is a lie!");
            }
        }


    }

    /**
     * Converts the first specific pressed Key to the slot in a given Inventory.
     * Does also accept Interaction (mapped to -101) and drop key (mapped to -102).
     *
     * @return The slot index in the currently managed inventory gui
     */
    protected abstract int specificKeyToSlotIndex();

    /**
     * Maps all specific keys to given indices. Can be used in specificKeyToSlotIndex()
     *
     * @param topLeft      top-left slot index
     * @param topCenter    top-center slot index
     * @param topRight     top-right slot index
     * @param centerLeft   center-left slot index
     * @param centerCenter center-center slot index
     * @param centerRight  center-right slot index
     * @param lowerLeft    lower-left slot index
     * @param lowerCenter  lower-center slot index
     * @param lowerRight   lower-right slot index
     * @return a slot index. wow!
     */
    int mapKeyToSlot(int topLeft, int topCenter, int topRight,
                     int centerLeft, int centerCenter, int centerRight,
                     int lowerLeft, int lowerCenter, int lowerRight) {

        if (Config.isKeyTopLeftPressed()) {
            return topLeft;
        } else if (Config.isKeyTopCenterPressed()) {
            return topCenter;
        } else if (Config.isKeyTopRightPressed()) {
            return topRight;
        } else if (Config.isKeyCenterLeftPressed()) {
            return centerLeft;
        } else if (Config.isKeyCenterCenterPressed()) {
            return centerCenter;
        } else if (Config.isKeyCenterRightPressed()) {
            return centerRight;
        } else if (Config.isKeyLowerLeftPressed()) {
            return lowerLeft;
        } else if (Config.isKeyLowerCenterPressed()) {
            return lowerCenter;
        } else if (Config.isKeyLowerRightPressed()) {
            return lowerRight;
        } else if (Config.isKeyInteractPressed()) {
            return -101;
        } else if (Config.isKeyDropPressed()) {
            return -102;
        } else {
            return -1;
        }

    }

    /**
     * Moves a full Stack from a slot to another.
     *
     * @param srcIndex  The Source Slot Index of the Container
     * @param destIndex The Destination Slot Index of the Container
     */
    void moveAll(int srcIndex, int destIndex) {

        ItemStack source = getItemStack(srcIndex);

        if (source == null) {
            Logger.debug("moveAll(i,i)", "Source ItemStack from Index == null");
        } else {
            move(srcIndex, destIndex, source.getCount());
        }

    }

    /**
     * Moves a specified amount of Items from a slot to another. [Based on
     * INVTW]
     *
     * @param srcIndex  The Source Slot Index of the Container
     * @param destIndex The Destination Slot Index of the Container
     * @param amount    The amount of items to move (can be bigger then Stack Size)
     */
    void move(int srcIndex, int destIndex, int amount) {

        // Stacks
        ItemStack source = getItemStack(srcIndex);
        ItemStack destination = getItemStack(destIndex);

        // Same Location?
        if (source == null) {
            return;
        } else if (srcIndex == destIndex) {
            return;
        }

        // Test for max. moving Amount
        int sourceSize = source.getCount();
        int movedAmount = Math.min(amount, sourceSize);

        // Clear goal slot (May fail on full inventory!); only available if not holdling
        if (destination != null && !source.isItemEqual(destination) && srcIndex >= 0) {
            moveStackToInventory(destIndex);
            destination = getItemStack(destIndex);
        }

        // Move some
        if (destination == null || source.isItemEqual(destination)) {

            if (srcIndex >= 0) {
                leftClick(srcIndex);
            }

            for (int i = 0; i < movedAmount; i++) {
                rightClick(destIndex);
            }

            // Move back
            if (movedAmount < sourceSize && srcIndex > 0) {
                leftClick(srcIndex);
            }

            Logger.info("move(i,i,i)", "Moved " + movedAmount + " from " + srcIndex + " to " + destIndex + "!");

        } else {
            Logger.info("move(i,i,i)", "Unable to move!");
        }
    }

    /**
     * Returns the ItemStack in a slot [Based on INVTW]
     *
     * @param index The index of the slot in the container
     * @return Returns the ItemStack
     */
    ItemStack getItemStack(int index) {

        if (index >= 0 && index < container.inventorySlots.size()) {

            Slot slot = (Slot) (container.inventorySlots.get(index));

            // NEW_1_11 No Null-Stacks anymore. Empty Stacks with air...
            ItemStack returnStack =  (slot == null) ? null : slot.getStack();

            if(returnStack.getCount() == 0 && returnStack.getItem() == Item.getItemFromBlock(Blocks.AIR)) {
                returnStack = null;
            }

            return returnStack;

        } else if (index == -1 && Util.isHoldingStack()) {
            return Util.getHeldStack();
        } else {

            Logger.debug("getItemStack(i)", "Invalid index");
            return null;

        }

    }

    /**
     * Moves a stack (held or not) to the next fitting inventory slot.
     *
     * @param sourceIndex A slot index of the source items
     */
    void moveStackToInventory(int sourceIndex) {

        // Moving Stack
        ItemStack stackToMove = null;

        // Get the stack, index or held, cleanup held stack
        if (sourceIndex == -1) {
            if (Util.isHoldingStack()) {
                stackToMove = Util.getHeldStack();
            }
        } else {
            stackToMove = getItemStack(sourceIndex);

            // Is there a currently held stack?
            if (Util.isHoldingStack()) {
                moveStackToInventory(-1);
            }
        }

        // Test stack
        if (stackToMove == null) {
            Logger.debug("moveStackToInvetory(i)", "Stack at sourceIndex not found.");
            return;
        }

        // Get destination index
        int destIndex = calcInventoryDestination(stackToMove);

        // Additional click on source index, if not held
        if (sourceIndex != -1) {
            leftClick(sourceIndex);
        }

        // Move the item
        if (destIndex == -1) { // -1 means: Found none, drop item
            leftClick(-999);
            Logger.info("moveStackToInventory(i)", "Dropped item from index " + sourceIndex + ".");
        } else {
            leftClick(destIndex);
            Logger.info("moveStackToInventory(i)", "Moved item from index " + sourceIndex + " to " + destIndex + ".");
        }

    }

    /**
     * Calculates the next fitting or free inventory slot.
     *
     * @param stackToMove The ItemType and sized Stack to move
     * @return A super cool inventory slot index! Or -1, if you are to dumb
     * to keep your bloody inventory sorted! WHY U NO USE INV TWEAKS?!
     */
    private int calcInventoryDestination(ItemStack stackToMove) {

        // First run: Try to find a nice stack to put items on additionally
        for (int i = getInventoryStartIndex(); i < container.inventorySlots.size(); i++) {

            ItemStack potentialGoalStack = getItemStack(i);

            if (potentialGoalStack != null && stackToMove != null) {
                if (potentialGoalStack.isItemEqual(stackToMove)) {
                    if (potentialGoalStack.getCount() + stackToMove.getCount() <= stackToMove.getMaxStackSize()) {
                        return i;
                    }
                }
            }
        }

        // Second run: Find a free slot
        for (int i = getInventoryStartIndex(); i < container.inventorySlots.size(); i++) {
            if (getItemStack(i) == null) {
                return i;
            }
        }

        // Third run: No slot found. Drop this shit!
        return -1;

    }

    /**
     * Returns the start index of the user inventory in the current Gui.
     *
     * @return A slot index
     */
    protected abstract int getInventoryStartIndex();

    protected abstract int getInteractionSlotIndex();

    protected abstract int[] getDropSlots();

    protected abstract void interact();

    /**
     * Executes a left mouse click on a slot. [Based on INVTW]
     *
     * @param index The index of the slot in the container
     */
    void leftClick(int index) {
        slotClick(index, false);
    }

    /**
     * Executes a right mouse click on a slot. [Based on INVTW]
     *
     * @param index The index of the slot in the container
     */
    void rightClick(int index) {
        slotClick(index, true);
    }

    /**
     * Executes a mouse click on a slot. [Based on INVTW]
     *
     * @param index      The index of the slot in the container
     * @param rightClick True, if the click is with the right mouse button
     */
    private void slotClick(int index, boolean rightClick) {

        Logger.info("slotClick(i,b)", "Clicked @ Slot " + index + " with data " + rightClick + ".");

        int rightClickData = (rightClick) ? 1 : 0;

        CraftingKeys.proxy.sendSlotClick(Util.client.playerController, container.windowId, index,
                rightClickData, 0, Util.client.player);

    }

}
