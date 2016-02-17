package com.projectkorra.projectkorra.waterbending;

import com.projectkorra.projectkorra.BendingPlayer;
import com.projectkorra.projectkorra.Element;
import com.projectkorra.projectkorra.GeneralMethods;
import com.projectkorra.projectkorra.ability.AirAbility;
import com.projectkorra.projectkorra.ability.BloodAbility;
import com.projectkorra.projectkorra.avatar.AvatarState;
import com.projectkorra.projectkorra.object.HorizontalVelocityTracker;
import com.projectkorra.projectkorra.util.TempPotionEffect;

import org.bukkit.Location;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class Bloodbending extends BloodAbility {

	private static final ConcurrentHashMap<Entity, Location> TARGETED_ENTITIES = new ConcurrentHashMap<Entity, Location>();

	private boolean canOnlyBeUsedAtNight;
	private boolean canBeUsedOnUndeadMobs;
	private boolean onlyUsableDuringMoon;
	private boolean canBloodbendOtherBloodbenders;
	private int range;
	private long time;
	private long holdTime;
	private long cooldown;
	private double throwFactor;
	private Entity target;

	public Bloodbending(Player player) {
		super(player);

		Bloodbending ability = getAbility(player, getClass());
		if (ability != null) {
			ability.remove();
			return;
		}

		this.canOnlyBeUsedAtNight = getConfig().getBoolean("Abilities.Water.Bloodbending.CanOnlyBeUsedAtNight");
		this.canBeUsedOnUndeadMobs = getConfig().getBoolean("Abilities.Water.Bloodbending.CanBeUsedOnUndeadMobs");
		this.onlyUsableDuringMoon = getConfig().getBoolean("Abilities.Water.Bloodbending.CanOnlyBeUsedDuringFullMoon");
		this.canBloodbendOtherBloodbenders = getConfig().getBoolean("Abilities.Water.Bloodbending.CanBloodbendOtherBloodbenders");
		this.range = getConfig().getInt("Abilities.Water.Bloodbending.Range");
		this.holdTime = getConfig().getInt("Abilities.Water.Bloodbending.HoldTime");
		this.cooldown = getConfig().getInt("Abilities.Water.Bloodbending.Cooldown");
		this.throwFactor = getConfig().getDouble("Abilities.Water.Bloodbending.ThrowFactor");

		if (canOnlyBeUsedAtNight && !isNight(player.getWorld()) && !bPlayer.canBloodbendAtAnytime()) {
			return;
		} else if (onlyUsableDuringMoon && !isFullMoon(player.getWorld()) && !bPlayer.canBloodbendAtAnytime()) {
			return;
		} else if (!bPlayer.canBend(this) && !bPlayer.isAvatarState()) {
			return;
		}

		range = (int) getNightFactor(range, player.getWorld());
		if (bPlayer.isAvatarState()) {
			range += AvatarState.getValue(1.5);
			for (Entity entity : GeneralMethods.getEntitiesAroundPoint(player.getLocation(), range)) {
				if (entity instanceof LivingEntity) {
					if (entity instanceof Player) {
						Player enemyPlayer = (Player) entity;
						BendingPlayer enemyBPlayer = BendingPlayer.getBendingPlayer(enemyPlayer);
						if (enemyBPlayer == null
								|| GeneralMethods.isRegionProtectedFromBuild(this, entity.getLocation()) 
								|| enemyBPlayer.isAvatarState()
								|| entity.getEntityId() == player.getEntityId() 
								|| enemyBPlayer.canBendIgnoreBindsCooldowns(this)) {
							continue;
						}
					}
					GeneralMethods.damageEntity(this, entity, 0);
					AirAbility.breakBreathbendingHold(entity);
					TARGETED_ENTITIES.put(entity, entity.getLocation().clone());
				}
			}
		} else {
			//Location location = GeneralMethods.getTargetedLocation(player, 6, getTransparentMaterial());
			//List<Entity> entities = GeneralMethods.getEntitiesAroundPoint(location, 1.5);
			List<Entity> entities = new ArrayList<Entity>();
			for (int i = 0; i < 6; i++) {
				Location location = GeneralMethods.getTargetedLocation(player, i, getTransparentMaterial());
				entities = GeneralMethods.getEntitiesAroundPoint(location, 1.7);
				if (entities.contains(player))
					entities.remove(player);
				if (entities != null && !entities.isEmpty() && !entities.contains(player)) {
					break;
				}
			}
			if (entities == null || entities.isEmpty()) {
				return;
			}
			target = entities.get(0);

			if (target == null || !(target instanceof LivingEntity) 
					|| GeneralMethods.isRegionProtectedFromBuild(this, target.getLocation())
					|| target.getEntityId() == player.getEntityId()) {
				return;
			} else if (target instanceof Player) {
				BendingPlayer targetBPlayer = BendingPlayer.getBendingPlayer(player);
				if (targetBPlayer != null) {
					if ((targetBPlayer.canBendIgnoreBindsCooldowns(ability) && !canBloodbendOtherBloodbenders) || targetBPlayer.isAvatarState()) {
						if (!isDay(target.getWorld()) || targetBPlayer.canBloodbendAtAnytime()) {
							return;
						}
					}
				}
			} else if (!canBeUsedOnUndeadMobs && isUndead(target)) {
				return;
			}

			GeneralMethods.damageEntity(this, target, 0);
			HorizontalVelocityTracker.remove(target);
			AirAbility.breakBreathbendingHold(target);
			TARGETED_ENTITIES.put(target, target.getLocation().clone());
		}

		if (TARGETED_ENTITIES.size() > 0) {
			bPlayer.addCooldown(this);
		}

		this.time = System.currentTimeMillis();
		start();
	}

	public static void launch(Player player) {
		Bloodbending bloodbending = getAbility(player, Bloodbending.class);
		if (bloodbending != null) {
			bloodbending.launch();
		}
	}

	private void launch() {
		Location location = player.getLocation();
		for (Entity entity : TARGETED_ENTITIES.keySet()) {
			Location target = entity.getLocation().clone();
			Vector vector = GeneralMethods.getDirection(location, GeneralMethods.getTargetedLocation(player, location.distance(target)));
			vector.normalize();
			entity.setVelocity(vector.multiply(throwFactor));
			new HorizontalVelocityTracker(entity, player, 200, "Bloodbending", Element.AIR);
		}
		remove();
	}

	@Override
	public void progress() {
		PotionEffect effect = new PotionEffect(PotionEffectType.SLOW, 60, 1);

		if (!player.isSneaking()) {
			TARGETED_ENTITIES.remove(target);
			remove();
			return;
		} else if (holdTime > 0 && System.currentTimeMillis() - this.time > holdTime) {
			remove();
			TARGETED_ENTITIES.remove(target);
			return;
		}

		if (!canBeUsedOnUndeadMobs) {
			for (Entity entity : TARGETED_ENTITIES.keySet()) {
				if (isUndead(entity)) {
					TARGETED_ENTITIES.remove(entity);
				}
			}
		}

		if (onlyUsableDuringMoon && !isFullMoon(player.getWorld()) && !bPlayer.canBloodbendAtAnytime()) {
			TARGETED_ENTITIES.remove(target);
			remove();
			return;
		} else if (canOnlyBeUsedAtNight && !isNight(player.getWorld()) && !bPlayer.canBloodbendAtAnytime()) {
			TARGETED_ENTITIES.remove(target);
			remove();
			return;
		} else if (!bPlayer.canBendIgnoreCooldowns(this)) {
			TARGETED_ENTITIES.remove(target);
			remove();
			return;
		}

		if (bPlayer.isAvatarState()) {
			ArrayList<Entity> entities = new ArrayList<>();

			for (Entity entity : GeneralMethods.getEntitiesAroundPoint(player.getLocation(), range)) {
				if (GeneralMethods.isRegionProtectedFromBuild(this, entity.getLocation())) {
					continue;
				} else if (entity instanceof Player) {
					BendingPlayer targetBPlayer = BendingPlayer.getBendingPlayer((Player) entity);
					if (targetBPlayer != null) {
						if (!targetBPlayer.canBeBloodbent() || entity.getEntityId() == player.getEntityId()) {
							continue;
						}
					}
				}

				entities.add(entity);
				if (!TARGETED_ENTITIES.containsKey(entity) && entity instanceof LivingEntity) {
					GeneralMethods.damageEntity(this, entity, 0);
					TARGETED_ENTITIES.put(entity, entity.getLocation().clone());
				}

				if (entity instanceof LivingEntity) {
					Location newLocation = entity.getLocation();
					if (player.getWorld() != newLocation.getWorld()) {
						TARGETED_ENTITIES.remove(entity);
						continue;
					}

					Location location = TARGETED_ENTITIES.get(entity);
					double distance = location.distance(newLocation);
					double dx, dy, dz;
					dx = location.getX() - newLocation.getX();
					dy = location.getY() - newLocation.getY();
					dz = location.getZ() - newLocation.getZ();
					Vector vector = new Vector(dx, dy, dz);

					if (distance > .5) {
						entity.setVelocity(vector.normalize().multiply(.5));
					} else {
						entity.setVelocity(new Vector(0, 0, 0));
					}

					new TempPotionEffect((LivingEntity) entity, effect);
					entity.setFallDistance(0);
					if (entity instanceof Creature) {
						((Creature) entity).setTarget(null);
					}
					AirAbility.breakBreathbendingHold(entity);
				}
			}

			for (Entity entity : TARGETED_ENTITIES.keySet()) {
				if (!entities.contains(entity)) {
					TARGETED_ENTITIES.remove(entity);
				}
			}
		} else {
			for (Entity entity : TARGETED_ENTITIES.keySet()) {
				if (entity instanceof Player) {
					BendingPlayer targetBPlayer = BendingPlayer.getBendingPlayer((Player) entity);
					if (targetBPlayer != null && !targetBPlayer.canBeBloodbent()) {
						TARGETED_ENTITIES.remove(entity);
						continue;
					}
				}

				Location newLocation = entity.getLocation();
				if (player.getWorld() != newLocation.getWorld()) {
					TARGETED_ENTITIES.remove(entity);
					continue;
				}

				Location location = GeneralMethods.getTargetedLocation(player, 6, getTransparentMaterial());
				double distance = location.distance(newLocation);
				double dx, dy, dz;
				dx = location.getX() - newLocation.getX();
				dy = location.getY() - newLocation.getY();
				dz = location.getZ() - newLocation.getZ();
				Vector vector = new Vector(dx, dy, dz);

				if (distance > .5) {
					entity.setVelocity(vector.normalize().multiply(.5));
				} else {
					entity.setVelocity(new Vector(0, 0, 0));
				}

				new TempPotionEffect((LivingEntity) entity, effect);
				entity.setFallDistance(0);
				if (entity instanceof Creature) {
					((Creature) entity).setTarget(null);
				}
				AirAbility.breakBreathbendingHold(entity);
			}
		}
	}

	public static boolean isBloodbent(Entity entity) {
		return entity != null ? TARGETED_ENTITIES.containsKey(entity) : null;
	}

	public static Location getBloodbendingLocation(Entity entity) {
		return entity != null ? TARGETED_ENTITIES.get(entity) : null;
	}

	@Override
	public String getName() {
		return "Bloodbending";
	}

	@Override
	public Location getLocation() {
		return player != null ? player.getLocation() : null;
	}

	@Override
	public long getCooldown() {
		return cooldown;
	}

	@Override
	public boolean isSneakAbility() {
		return true;
	}

	@Override
	public boolean isHarmlessAbility() {
		return false;
	}

	public boolean isCanOnlyBeUsedAtNight() {
		return canOnlyBeUsedAtNight;
	}

	public void setCanOnlyBeUsedAtNight(boolean canOnlyBeUsedAtNight) {
		this.canOnlyBeUsedAtNight = canOnlyBeUsedAtNight;
	}

	public boolean isCanBeUsedOnUndeadMobs() {
		return canBeUsedOnUndeadMobs;
	}

	public void setCanBeUsedOnUndeadMobs(boolean canBeUsedOnUndeadMobs) {
		this.canBeUsedOnUndeadMobs = canBeUsedOnUndeadMobs;
	}

	public boolean isOnlyUsableDuringMoon() {
		return onlyUsableDuringMoon;
	}

	public void setOnlyUsableDuringMoon(boolean onlyUsableDuringMoon) {
		this.onlyUsableDuringMoon = onlyUsableDuringMoon;
	}

	public boolean isCanBloodbendOtherBloodbenders() {
		return canBloodbendOtherBloodbenders;
	}

	public void setCanBloodbendOtherBloodbenders(boolean canBloodbendOtherBloodbenders) {
		this.canBloodbendOtherBloodbenders = canBloodbendOtherBloodbenders;
	}

	public int getRange() {
		return range;
	}

	public void setRange(int range) {
		this.range = range;
	}

	public long getTime() {
		return time;
	}

	public void setTime(long time) {
		this.time = time;
	}

	public long getHoldTime() {
		return holdTime;
	}

	public void setHoldTime(long holdTime) {
		this.holdTime = holdTime;
	}

	public double getThrowFactor() {
		return throwFactor;
	}

	public void setThrowFactor(double throwFactor) {
		this.throwFactor = throwFactor;
	}
	
	public Entity getTarget() {
		return target;
	}

	public void setCooldown(long cooldown) {
		this.cooldown = cooldown;
	}

}
