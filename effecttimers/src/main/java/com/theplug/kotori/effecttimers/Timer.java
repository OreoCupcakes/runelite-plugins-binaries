/*
 * Copyright (c) 2020 ThatGamerBlue
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.theplug.kotori.effecttimers;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Set;

import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.ToString;
import net.runelite.api.*;
import net.runelite.api.kit.KitType;

@ToString
@EqualsAndHashCode
public class Timer
{
	private EffectTimersPlugin plugin;
	private Client client;
	@Setter
	private int ticksStart;
	@Setter
	private long startMillis;
	@Setter
	private int ticksLength;
	private int cooldownLength;
	private TimerType type;
	private boolean shutdown = false;
	private final Set<Integer> swampBarkArmorIds = Set.of(ItemID.SWAMPBARK_BODY, ItemID.SWAMPBARK_BOOTS, ItemID.SWAMPBARK_HELM, ItemID.SWAMPBARK_GAUNTLETS, ItemID.SWAMPBARK_LEGS);

	public Timer(EffectTimersPlugin plugin, PlayerEffect effect)
	{
		this(plugin, effect, false, null, null);
	}

	public Timer(EffectTimersPlugin plugin, PlayerEffect effect, boolean half, Actor actorWithGraphic, Player yourOpponent)
	{
		this.plugin = plugin;
		this.client = plugin.getClient();
		this.ticksStart = client.getTickCount();
		this.startMillis = System.currentTimeMillis();
		this.type = effect == null ? null : effect.getType();
		this.cooldownLength = effect == null ? 0 : effect.getType().getImmunityLength();
		int length = effect == null ? 0 : half ? effect.getTimerLengthTicks() / 2 : effect.getTimerLengthTicks();
		if (type == TimerType.FREEZE)
		{
			length = determineFreezeLength(effect, actorWithGraphic, yourOpponent);
		}
		this.ticksLength = length;
	}

	public boolean isValid()
	{
		return type != null;
	}

	public int getTicksElapsed()
	{
		return client.getTickCount() - ticksStart;
	}

	public TimerState getTimerState()
	{
		int ticksElapsed = getTicksElapsed();
		if (ticksElapsed > ticksLength + cooldownLength)
		{
			return TimerState.INACTIVE;
		}
		else if (ticksElapsed > ticksLength)
		{
			return TimerState.COOLDOWN;
		}
		return TimerState.ACTIVE;
	}

	public void setTimerTypeIfNull(TimerType set)
	{
		if (type == null)
		{
			type = set;
		}
	}

	public long getMillisForRender()
	{
		long millisElapsed = System.currentTimeMillis() - startMillis;
		long millisRemaining = ((ticksLength * 600) + (cooldownLength * 600)) - millisElapsed;
		switch (getTimerState())
		{
			case ACTIVE:
				return millisRemaining - (cooldownLength * 600);
			case COOLDOWN:
				return millisRemaining;
			default:
				return -1;
		}
	}

	public int getTicksForRender()
	{
		int ticksRemaining = (ticksLength + cooldownLength) - getTicksElapsed();
		ticksRemaining++; // so it renders nicely
		switch (getTimerState())
		{
			case ACTIVE:
				return ticksRemaining - cooldownLength;
			case COOLDOWN:
				return ticksRemaining;
			default:
				return -1;
		}
	}

	public BufferedImage getIcon()
	{
		return getTimerState() == TimerState.COOLDOWN ? type.getCooldownIcon() : type.getIcon();
	}

	public Color determineColor()
	{
		if (plugin.getConfig().showIcons())
		{
			if (plugin.getConfig().setColors())
			{
				return getTimerState() == TimerState.COOLDOWN ? plugin.getConfig().cooldownColor() : plugin.getConfig().timerColor();
			}
			else
			{
				return getTimerState() == TimerState.COOLDOWN ? type.getDefaultColor().darker() : type.getDefaultColor();
			}
		}
		if (!plugin.getConfig().showIcons())
		{
			if (plugin.getConfig().setColors())
			{
				return getTimerState() == TimerState.COOLDOWN ? plugin.getConfig().cooldownColor() : plugin.getConfig().timerColor();
			}
			else
			{
				return getTimerState() == TimerState.COOLDOWN ? type.getDefaultColor().darker() : type.getDefaultColor();
			}
		}
		else
		{
			return getTimerState() == TimerState.COOLDOWN ? type.getDefaultColor().darker() : type.getDefaultColor();
		}
	}
	
	private int determineFreezeLength(PlayerEffect effect, Actor actorWithGraphic, Player yourOpponent)
	{
		if (effect == null)
		{
			return 0;
		}
		
		int lengthOfFreeze = effect.getTimerLengthTicks();
		
		if (actorWithGraphic == null)
		{
			return lengthOfFreeze;
		}
		
		if (actorWithGraphic.equals(client.getLocalPlayer()))
		{
			if (yourOpponent == null)
			{
				return lengthOfFreeze;
			}
			
			PlayerComposition opponentInfo = yourOpponent.getPlayerComposition();
			if (opponentInfo == null)
			{
				return lengthOfFreeze;
			}
			
			int[] opponentEquipmentIds = opponentInfo.getEquipmentIds();
			
			switch (effect)
			{
				case RUSH:
				case BURST:
				case BLITZ:
				case BARRAGE:
					int weaponItemId = opponentEquipmentIds[KitType.WEAPON.getIndex()] - 512;
					switch (weaponItemId)
					{
						case ItemID.ANCIENT_SCEPTRE:
							lengthOfFreeze += lengthOfFreeze * 10 / 100;
							break;
						case ItemID.ICE_ANCIENT_SCEPTRE:
							lengthOfFreeze += lengthOfFreeze * 35 / 100;
							break;
					}
					break;
				case BIND:
				case SNARE:
				case ENTANGLE:
					int numPiecesOfSwampBark = 0;
					for (int kitId : opponentEquipmentIds)
					{
						int itemId = kitId - 512;
						if (swampBarkArmorIds.contains(itemId))
						{
							numPiecesOfSwampBark++;
						}
					}
					lengthOfFreeze += numPiecesOfSwampBark;
					break;
			}
		}
		else
		{
			ItemContainer equipmentContainer = client.getItemContainer(InventoryID.EQUIPMENT);
			if (equipmentContainer != null)
			{
				Item[] equipment = equipmentContainer.getItems();
				
				switch (effect)
				{
					case RUSH:
					case BURST:
					case BLITZ:
					case BARRAGE:
						Item weapon = equipment[EquipmentInventorySlot.WEAPON.getSlotIdx()];
						if (weapon != null)
						{
							int weaponId = weapon.getId();
							switch (weaponId)
							{
								case ItemID.ANCIENT_SCEPTRE:
									lengthOfFreeze += lengthOfFreeze * 10 / 100;
									break;
								case ItemID.ICE_ANCIENT_SCEPTRE:
									lengthOfFreeze += lengthOfFreeze * 35 / 100;
									break;
							}
						}
						break;
					case BIND:
					case SNARE:
					case ENTANGLE:
						int numPiecesOfSwampBark = 0;
						for (Item gear : equipment)
						{
							if (swampBarkArmorIds.contains(gear.getId()))
							{
								numPiecesOfSwampBark++;
							}
						}
						lengthOfFreeze += numPiecesOfSwampBark;
						break;
				}
				
			}
			
			if (actorWithGraphic instanceof NPC)
			{
				NPC npc = (NPC) actorWithGraphic;
				switch (npc.getId())
				{
					case NpcID.PHANTOM_MUSPAH:
					case NpcID.PHANTOM_MUSPAH_12078:
					case NpcID.PHANTOM_MUSPAH_12079:
						lengthOfFreeze = lengthOfFreeze * 2 / 3;
						break;
				}
			}
		}
		
		return lengthOfFreeze;
	}

	public enum TimerState
	{
		ACTIVE,
		COOLDOWN,
		INACTIVE
	}
}
