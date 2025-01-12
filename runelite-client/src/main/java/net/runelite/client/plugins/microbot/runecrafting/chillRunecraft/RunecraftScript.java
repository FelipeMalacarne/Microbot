package net.runelite.client.plugins.microbot.runecrafting.chillRunecraft;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;


public class RunecraftScript extends Script
{
    private States state;
    private Altars altar;
    private boolean initialise;
    public static int runsCompleted = 0;
    public static int initialRunecraftLevel;
    public static int runecraftLevel;
    public static int initialRunecraftXp;
    public static int runecraftXp;

    public boolean run(RunecraftConfig config)
    {
        Microbot.enableAutoRunOn = true;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_RUNECRAFT);

        altar = config.ALTAR();

        initialise = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                long startTime = System.currentTimeMillis();

                if (initialise)
                {
                    initialRunecraftXp = Microbot.getClient().getSkillExperience(Skill.RUNECRAFT);
                    initialRunecraftLevel = Rs2Player.getRealSkillLevel(Skill.RUNECRAFT);

                    if (!Rs2Inventory.hasItem(altar.getTalismanName()))
                    {
                        System.out.println("No Talisman in inventory - banking");
                        state = States.BANKING;
                        initialise = false;
                        return;
                    }

                    if (!Rs2Inventory.hasItem("Pure Essence", false))
                    {
                        System.out.println("No essence in inventory - banking");
                        state = States.BANKING;
                        initialise = false;
                        return;
                    }
                    state = States.WALKING_TO_ALTAR;
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Microbot.pauseAllScripts) return;

                if (Rs2Player.isInteracting()) return;

                switch(state)
                {
                    case BANKING:
                        initialise = false;
                        boolean isBankOpen = Rs2Bank.walkToBankAndUseBank();
                        Microbot.status = "Walking to bank";
                        if (!isBankOpen || !Rs2Bank.isOpen()) return;

                        if (!Rs2Inventory.hasItem(altar.getTalismanName()))
                        {
                            Microbot.status = "Withdrawing Talisman";
                            System.out.println("Checking Talisman");
                            if (!Rs2Bank.hasBankItem(altar.getTalismanName()))
                            {
                                Microbot.showMessage("No talisman in bank!");
                                shutdown();
                                return;
                            }
                            Rs2Bank.withdrawOne(altar.getTalismanName());
                            Rs2Random.wait(800,1600);
                        }

                        if (Rs2Inventory.hasItem(altar.getRuneName(), false))
                        {
                            System.out.println("Depositing Runes");
                            Microbot.status = "Depositing runes";
                            Rs2Bank.depositAll(altar.getRuneName(), false);
                            Rs2Random.wait(800, 1600);
                        }

                        System.out.println("Withdrawing Essence");
                        Microbot.status = "Withdrawing pure essence";

                        if (!Rs2Bank.hasBankItem("Pure essence", false))
                        {
                            Microbot.showMessage("No pure essence in bank!");
                            shutdown();
                            return;
                        }
                        Rs2Bank.withdrawAll("Pure essence", true);
                        Rs2Random.wait(800, 1600);
                        state = States.WALKING_TO_ALTAR;
                        Rs2Bank.closeBank();
                        break;

                    case WALKING_TO_ALTAR:
                        initialise = false;
                        System.out.println("Walking to Altar");
                        Microbot.status = "Walking to altar";
                        boolean walked = Rs2Walker.walkTo(altar.getAltarWorldPoint(), 2);
                        if (!walked) return;
                        Rs2Random.wait(800, 1600);
                        state = States.ENTERING_ALTAR;
                        break;

                    case ENTERING_ALTAR:
                        initialise = false;
                        System.out.println("Entering Altar");
                        Microbot.status = "Entering altar";
                        Rs2Inventory.useItemOnObject(altar.getTalismanID(), altar.getAltarRuinsID());
                        Rs2Random.wait(800, 1600);
                        sleepUntil(() -> !Rs2Player.isMoving());
                        Rs2Random.wait(2000, 2400);
                        state = States.CRAFTING_RUNES;
                        break;

                    case CRAFTING_RUNES:
                        initialise = false;
                        System.out.println("Crafting Runes");
                        Microbot.status = "Crafting runes";
                        Rs2Inventory.useItemOnObject(ItemID.PURE_ESSENCE, altar.getAltarID());
                        Rs2Random.wait(800, 1600);
                        sleepUntil(() -> Rs2Player.waitForXpDrop(Skill.RUNECRAFT));
                        Rs2Random.wait(800, 1600);
                        runsCompleted++;
                        updateLevelXp();
                        state = States.EXITING_ALTAR;
                        break;

                    case EXITING_ALTAR:
                        Microbot.status = "Exiting altar";
                        Rs2GameObject.interact(altar.getPortalID(), "Use");
                        sleepUntil(() -> !Rs2Player.isMoving());
                        Rs2Random.wait(800, 1600);
                        state = States.BANKING;
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex)
            {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    public void updateLevelXp()
    {
        runecraftLevel = Rs2Player.getRealSkillLevel(Skill.RUNECRAFT);
        runecraftXp = Microbot.getClient().getSkillExperience(Skill.RUNECRAFT);
    }
}