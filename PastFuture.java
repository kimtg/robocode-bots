// (C) 2007-2019 Kim, Taegyoon

// StatisticLogTargeting
// version 1.0: 20071023. matches only on fire times
// version 1.0b: 20071024. matches always
// version 1.1: movement improvements
// version 1.1b: stay away
// version 1.1c: stay away at first of the round
// version 1.2: weights on real fires
// version 1.2a: removed randomness in movement
// version 1.2b: 20100410 bug fixes(battle field size, radar, etc.)
// version 2.0: 20100411 added melee mode: melee radar, melee targeting, minimum risk movement
// version 2.1: changed fire power, last bearing offset, waiting gun turn
// version 2.1.1: fire power, gun stat decaying
// version 2.1.2: TeamRobot, melee: area targeting, adaptive flattening!
// version 2.1.3: flattening on/off
// version 2.1.4: area targeting bugfix (nearest selection)
// version 2.1.5: surfing bugfix, melee tweaks
// version 2.1.6: MIN_SAMPLES, LAST_BEARING_OFFSET_INDEXS, anti bullet catcher
// version 2.1.7: setFire(3), inField
// version 2.1.8: setFire(2.0), LAST_BEARING_OFFSET_INDEXS, waiting gun turn, gun stat decaying
// version 2.1.9: no flattening
// version 2.2.0: prevent wall hit
// version 2.2.1: fixed bullet catcher detection bug
// version 2.3.0: per-robot segmentation in gun
// version 2.3.1: per-robot segmentation in movement
// version 2.3.2: removed Java 11's warnings

package stelo;

import robocode.*;
import robocode.util.Utils;

import java.awt.geom.*;     // for Point2D's
import java.util.*;
import java.awt.*;

public class PastFuture extends TeamRobot {
    private static final int BINS = 47; // 47
	private static final int MIDDLE_BIN = (BINS - 1) / 2;
	private static final int DISTANCE_INDEXES = 5;
	private static final int VELOCITY_INDEXES = 5;
	private static final int WALL_INDEXES = 2;
	private static final int LAST_BEARING_OFFSET_INDEXS = 7; // 9
	private static final int ROBOT_INDEXES = 20;
//    public static double _surfStats[] = new double[BINS]; // we'll use 47 bins
	private static double _surfStats1[][][] = new double[ROBOT_INDEXES][DISTANCE_INDEXES][BINS];
	private static double _surfStats2[][][] = new double[ROBOT_INDEXES][VELOCITY_INDEXES][BINS];
	
	private static double _surfStats3[][][][] = new double[ROBOT_INDEXES][VELOCITY_INDEXES][DISTANCE_INDEXES][BINS];
	private static double _surfStats4[][][][] = new double[ROBOT_INDEXES][VELOCITY_INDEXES][VELOCITY_INDEXES][BINS];
	
    private static double _surfStats5[][][][][] = new double[ROBOT_INDEXES][VELOCITY_INDEXES][VELOCITY_INDEXES][DISTANCE_INDEXES][BINS];
	private static double _surfStats6[][][][][][] = new double[ROBOT_INDEXES][VELOCITY_INDEXES][VELOCITY_INDEXES][DISTANCE_INDEXES][WALL_INDEXES][BINS];
    private static double _surfStats7[][][][][][][] = new double[ROBOT_INDEXES][VELOCITY_INDEXES][VELOCITY_INDEXES][DISTANCE_INDEXES][WALL_INDEXES][LAST_BEARING_OFFSET_INDEXS][BINS];

    public static Point2D.Double _myLocation;     // our bot's location
    public static Point2D.Double _enemyLocation;  // enemy bot's location
	public static Point2D.Double _lastEnemyLocation;  // enemy bot's location

    public ArrayList<EnemyWave> _enemyWaves;
    public ArrayList<Integer> _surfDirections;
    public ArrayList<Double> _surfAbsBearings;
	
	private static double lateralDirection;
	private static double lastEnemyVelocity;

    // This is a rectangle that represents an 800x600 battle field,
    // used for a simple, iterative WallSmoothing method (by Kawigi).
    // If you're not familiar with WallSmoothing, the wall stick indicates
    // the amount of space we try to always have on either end of the tank
    // (extending straight out the front or back) before touching a wall.
//    public static Rectangle2D.Double _fieldRect
//       = new java.awt.geom.Rectangle2D.Double(18, 18, 764, 564);
    public static Rectangle2D.Double _fieldRect
       = new java.awt.geom.Rectangle2D.Double(18, 18, 800+1-18*2, 600+1-18*2);
    public static Rectangle2D.Double _moveFieldRect
       = new java.awt.geom.Rectangle2D.Double(25, 25, 800+1-25*2, 600+1-25*2);

    private static double lastEnemyEnergy = 100.0;
    private static double enemyBulletPower = 0.1;
    private static double lastMyVelocity;

	private static Vector<Double> velocities = new Vector<>(10000);
	private static Vector<Double> headingChanges = new Vector<>(10000);
//	private static Vector sinHeadingChanges = new Vector(10000); // for speeding calculation
//	private static Vector cosHeadingChanges = new Vector(10000);
	
	private static final int SLT_VELOCITY_INDEXES = 5;
	private static final int ACCEL_INDEXES = 5;

	// different segmentations
	private static Vector[][][][][][] fireTimes5 = new Vector[ROBOT_INDEXES][SLT_VELOCITY_INDEXES][ACCEL_INDEXES][DISTANCE_INDEXES][WALL_INDEXES][LAST_BEARING_OFFSET_INDEXS];
	private static Vector[][][][][] fireTimes4 = new Vector[ROBOT_INDEXES][SLT_VELOCITY_INDEXES][ACCEL_INDEXES][DISTANCE_INDEXES][WALL_INDEXES];
	private static Vector[][][][] fireTimes3 = new Vector[ROBOT_INDEXES][SLT_VELOCITY_INDEXES][ACCEL_INDEXES][DISTANCE_INDEXES];
	private static Vector[][][] fireTimes2 = new Vector[ROBOT_INDEXES][SLT_VELOCITY_INDEXES][ACCEL_INDEXES];
	private static Vector[][] fireTimes1 = new Vector[ROBOT_INDEXES][SLT_VELOCITY_INDEXES];
	private static Vector[] fireTimes0 = new Vector[ROBOT_INDEXES];
	private static Vector<Integer> realFireTimes = new Vector<>();
			
	private static double MAX_ESCAPE_ANGLE = 0.9;
	private static double lastEnemyHeading = Double.POSITIVE_INFINITY;
	private static double enemyDistance;
	private static double absBearing;
	private static double lastBearingOffset;
	
	private static double enemyFire, enemyHit;
	private static double numFire, numHit, numBulletHitBullet;
	private static double lastExactBearingOffset;
	private static int myLastExactBearingOffsetIndex;
	private boolean enemyFiredThisRound;
	private static double enemyVelocity;
	private static double hitRate, enemyHitRate;
	private static double enemyDirection = 1.0;
	private static Rectangle2D.Double field = new java.awt.geom.Rectangle2D.Double(0, 0, 800, 600);
	private static Point2D.Double center = new Point2D.Double(400, 300);
	private static String lastTargetName = "";
//	private static long fireTime = 0;

	private static HashMap<String, Integer> robotNum = new HashMap<>();
	private static int robotNameToNum(String name) {
		Integer num = robotNum.get(name);
		if (num != null) {
			return num;
		} else {
			num = robotNum.size();
			robotNum.put(name, num);
			return num;
		}
	}

	static class EnemyInfo {
		public ScannedRobotEvent sre;
		public Point2D.Double location;
		public Point2D.Double firstLocation;
		public EnemyInfo(ScannedRobotEvent _sre, Point2D.Double _location) {
			sre = _sre;
			location = _location;
			firstLocation = (Point2D.Double) location.clone();
		}
	}	
	private static HashMap<String, EnemyInfo> enemyInfos; // for melee
	private static Graphics2D g;
			    
    public void run() {
		g = getGraphics();
		setColors(Color.GREEN, Color.YELLOW, Color.RED);
		setBulletColor(Color.GREEN);
		lateralDirection = 1;
		lastEnemyVelocity = 0;
		
        _enemyWaves = new ArrayList<>();
        _surfDirections = new ArrayList<>();
        _surfAbsBearings = new ArrayList<>();

		_myLocation = new Point2D.Double(getX(), getY());
		enemyInfos = new HashMap<>();

        setAdjustGunForRobotTurn(true);
//        setAdjustRadarForGunTurn(true);
		setAdjustRadarForRobotTurn(true);

		if (getRoundNum() == 0) {
			for (int h = 0; h < ROBOT_INDEXES; h++) {
				fireTimes0[h] = new Vector();
				for (int i = 0; i < SLT_VELOCITY_INDEXES; i++) {
					fireTimes1[h][i] = new Vector();
					for (int j = 0; j < ACCEL_INDEXES; j++) {
						fireTimes2[h][i][j] = new Vector();
						for (int k = 0; k < DISTANCE_INDEXES; k++) {
							fireTimes3[h][i][j][k] = new Vector();
							for (int l = 0; l < WALL_INDEXES; l++) {
								fireTimes4[h][i][j][k][l] = new Vector();
								for (int m = 0; m < LAST_BEARING_OFFSET_INDEXS; m++) {
									fireTimes5[h][i][j][k][l][m] = new Vector();
								}												
							}						
						}
					}
				}
			}
		}
		field = new java.awt.geom.Rectangle2D.Double(0, 0, getBattleFieldWidth(), getBattleFieldHeight());
		_fieldRect = new java.awt.geom.Rectangle2D.Double(18, 18, getBattleFieldWidth() + 1 - 18*2, getBattleFieldHeight() + 1 - 18*2);
		_moveFieldRect = new java.awt.geom.Rectangle2D.Double(25, 25, getBattleFieldWidth() + 1 - 25*2, getBattleFieldHeight() + 1 - 25*2);
		center = new Point2D.Double(field.getWidth() / 2.0, field.getHeight() / 2.0);
		EnemyWave.MAX_DISTANCE = Math.hypot(getBattleFieldWidth(), getBattleFieldHeight());
		enemyInfos.clear();
		ramming = false;
		
		if (enemyFire > 0) {
			double decayFactor = enemyFire / 20.0;
			enemyFire /= decayFactor; // decay
			enemyHit /= decayFactor;
			numBulletHitBullet /= decayFactor;
		}
		System.out.println("My Hit Rate:    " + hitRate + "\nEnemy Hit Rate: " + enemyHitRate);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        do {
            // basic mini-radar code
	        setAdjustRadarForGunTurn(false);
			
			setTurnGunRightRadians(Double.POSITIVE_INFINITY);
			move(false);
            turnRadarRightRadians(Double.POSITIVE_INFINITY);
        } while (true);
    }

    public void onScannedRobot(ScannedRobotEvent e) {
		// finding the best target
		_myLocation = new Point2D.Double(getX(), getY());
		absBearing = e.getBearingRadians() + getHeadingRadians();
		Point2D.Double enemyLocation = (Point2D.Double) project(_myLocation, absBearing, e.getDistance());
		if (!isTeammate(e.getName())) { // update enemy info
			enemyInfos.put(e.getName(), new EnemyInfo(e, enemyLocation));
		}
		EnemyInfo lastInfo = (EnemyInfo) enemyInfos.get((Object) lastTargetName);
		EnemyInfo eInfo = lastInfo;
		if (eInfo == null) eInfo = (EnemyInfo) enemyInfos.get((Object) e.getName());
/*		
		Set infosSet = enemyInfos.entrySet();
		Iterator itr = infosSet.iterator();
		double minDistance = eInfo.location.distance(_myLocation) + center.distance(enemyLocation) / 5.0; //
		
		while (itr.hasNext()) {
			Map.Entry me = (Map.Entry) itr.next();
			EnemyInfo info = (EnemyInfo) me.getValue();
			ScannedRobotEvent sre = info.sre;
			double d = info.location.distance(_myLocation) + center.distance(info.location) / 5.0; //
			if (sre.getEnergy() == 0.0) { // disabled bot
				eInfo = info;
				break;
			}
			if (d < minDistance - 70.0) {
				minDistance = d;
				eInfo = info;
			}
		}
*/
		eInfo = selectEnemy();
		if (eInfo == null) eInfo = (EnemyInfo) enemyInfos.get((Object) e.getName());
		e = eInfo.sre;
		enemyLocation = eInfo.location;
		// absBearing = e.getBearingRadians() + getHeadingRadians();
		absBearing = Math.atan2(enemyLocation.getX() - _myLocation.getX(), enemyLocation.getY() - _myLocation.getY());
		
		if (eInfo.sre.getName().compareTo(lastTargetName) != 0) out.println("Target: " + eInfo.sre.getName());
		lastTargetName = eInfo.sre.getName();
		
        setAdjustRadarForGunTurn(true);
		
        double lateralVelocity = getVelocity()*Math.sin(e.getBearingRadians());
        
//		enemyDistance = e.getDistance();
		enemyDistance = eInfo.location.distance(_myLocation);
		enemyVelocity = e.getVelocity();
		
		if (enemyVelocity != 0)
			enemyDirection = Math.signum(enemyVelocity);
		double myVelocity = getVelocity();
				
        // setTurnRadarRightRadians(Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians()) * 2); // * 2

		// radar
		if (getOthers() <= 1 || e.getDistance() < 50) { // lock
			// double extraTurn = Math.min(Math.atan(36.0 / enemyDistance), Math.PI / 4.0);
			double extraTurn = Math.min(Math.atan(50.0 / enemyDistance), Math.PI / 4.0);
			double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
			setTurnRadarRightRadians(radarTurn + Math.signum(radarTurn) * extraTurn);
		} else { // melee
			if (getGunHeat() < 0.6) { // gun heat lock: lock for 6 ticks
				double extraTurn = Math.PI / 4.0;
				double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
				setTurnRadarRightRadians(radarTurn + Math.signum(radarTurn) * extraTurn);
			}		 	
		}
		{
			velocities.add(e.getVelocity());
			if (lastEnemyHeading == Double.POSITIVE_INFINITY) lastEnemyHeading = e.getHeadingRadians();
			double hc = Utils.normalRelativeAngle(e.getHeadingRadians() - lastEnemyHeading);
			headingChanges.add(hc);
//			sinHeadingChanges.add(new Double(Math.sin(hc)));
//			cosHeadingChanges.add(new Double(Math.cos(hc)));
		}
	
        _surfDirections.add(0,
            (lateralVelocity >= 0) ? 1 : -1);
        _surfAbsBearings.add(0, absBearing + Math.PI);
		
		int rn = robotNameToNum(e.getName());
	
        // check energyDrop
        double energyDrop = lastEnemyEnergy - e.getEnergy();
        if (energyDrop < 3.01 && energyDrop > 0.09
            && _surfDirections.size() > 2) {
        	enemyBulletPower = energyDrop;
			enemyFiredThisRound = true;
//            EnemyWave ew = new EnemyWave();

			EnemyWave surfWave = getClosestSurfableWave();
			if (surfWave != null)
        		myLastExactBearingOffsetIndex = (int) (Math.abs(getFactorIndex(surfWave, _myLocation) - MIDDLE_BIN) / MIDDLE_BIN * (LAST_BEARING_OFFSET_INDEXS - 1));
            EnemyWave ew = new EnemyWave(rn, this.getVelocity(), lastMyVelocity, enemyDistance, enemyBulletPower, myLastExactBearingOffsetIndex);
            ew.fireTime = getTime() - 1;
            ew.distanceTraveled = bulletVelocity(enemyBulletPower);
            ew.direction = ((Integer)_surfDirections.get(2)).intValue();
            ew.directAngle = ((Double)_surfAbsBearings.get(2)).doubleValue();
            ew.fireLocation = (Point2D.Double)_lastEnemyLocation.clone(); // last tick, because that needs the previous enemy location as the source of the wave
			ew.firstLocation = (Point2D.Double)_myLocation.clone();

            _enemyWaves.add(ew);
			enemyFire++;
        }
		
		hitRate =  numHit / numFire;
		enemyHitRate = enemyHit / enemyFire;
/*		if (enemyHitRate > 0.16) {
			if (!flattening) System.out.println("Flattening enabled.");
			flattening = true;
		} else if (enemyHitRate < 0.15) {
			if (flattening) System.out.println("Flattening disabled.");
			flattening = false;
		}
*/
        updateWaves();
        //doSurfing();
		move(false);

        // update after EnemyWave detection, because that needs the previous
        // enemy location as the source of the wave
        _enemyLocation = (Point2D.Double) project(_myLocation, absBearing, e.getDistance());
//		System.out.println(_enemyLocation.getX() + " " + _enemyLocation.getY());
		
		// double bulletPower = 1.9; // 1.9
		double bulletPower = 2.0; // 1.999
		// if (getEnergy() < e.getEnergy()) bulletPower = enemyBulletPower / 2.0;
		// if (getOthers() > 7) bulletPower += (double) (getOthers() - 7) * (3.0 - bulletPower) / 3.0;
		// double bulletPower = getOthers() > 7 ? 3.0 : 1.999; // 1.9
		// double bulletPower = limit(1.9, 1.9 + (3.0 - 1.9) / 10.0 * getOthers(), 3.0);
		//bulletPower = Math.min(bulletPower, getEnergy() / 5.0);
		if (getOthers() == 1 && enemyBulletPower < 0.11 && numBulletHitBullet / enemyFire > 0.2) { // for bullet catcher
			bulletPower = 0.1;
			out.println("Bullet catcher detected.");
		}
		if (enemyDistance < 50 || numFire > 0 && hitRate > 0.5) bulletPower = 3.0;
//		bulletPower = Math.min(getEnergy() - 0.01, Math.min(bulletPower, e.getEnergy() / 4.0));
		double ee = e.getEnergy();
		// bulletPower = Math.min(getEnergy() - 0.001, Math.min(bulletPower, ee > 4.0 ? (ee + 2.0) / 6.0 : ee / 4.0));
		bulletPower = Math.min(getEnergy() / 4.0, Math.min(bulletPower, ee > 4.0 ? (ee + 2.0) / 6.0 : ee / 4.0));
		bulletPower = limit(0.1, bulletPower, 3.0);
		
		Wave wave = new Wave(this);
		wave.gunLocation = new Point2D.Double(getX(), getY());
		Wave.targetLocation = _enemyLocation;
		wave.lateralDirection = lateralDirection;
		wave.bulletPower = bulletPower;
		wave.bearing = absBearing;
		addCustomEvent(wave);
		lastExactBearingOffset = wave.lastBearingOffset();
		
		int vIndex1 = (int) Math.abs(enemyVelocity / 2.0);
		// int accelIndex = (int) limit(0.0, (Math.abs(enemyVelocity - lastEnemyVelocity)), 2.0);
		int accelIndex = (int) limit(0.0, (enemyVelocity - lastEnemyVelocity) + 2.0, ACCEL_INDEXES - 1);
		int distanceIndex = (int)(enemyDistance / (EnemyWave.MAX_DISTANCE / DISTANCE_INDEXES));
		int wallIndex = !_fieldRect.contains(project(_enemyLocation, e.getHeadingRadians(), 200.0 * Math.signum(enemyVelocity))) ? 1 : 0;
		// int wallIndex = wallDistance(_enemyLocation) < 70.0 ? 0 : 1;
		int lastBearingOffsetIndex = (int) ((limit(0.0, Math.abs(lastExactBearingOffset), 0.9) ) / 0.9 * (LAST_BEARING_OFFSET_INDEXS - 1));
		
//		if (e.getEnergy() == 0.0 || (getOthers() > 1 && e.getDistance() > 50))
		if (e.getEnergy() == 0.0) {
			lastBearingOffset = 0.0;
		} else {
			lastBearingOffset = bestBearingOffset(e, bulletPower, absBearing, vIndex1, accelIndex, distanceIndex, wallIndex, lastBearingOffsetIndex);
		}

		double extraTurn = Math.atan(18.0 / enemyDistance);			     
//		if (!ramming && getGunHeat() < getGunCoolingRate() * 5.0) {
//		if (!ramming) {
//		if (!ramming && Math.abs(getGunTurnRemainingRadians()) - extraTurn <= Rules.GUN_TURN_RATE_RADIANS && getEnergy() > 0.1) {
//		if (!ramming && fireTime == getTime() && getGunTurnRemaining() == 0 && getEnergy() > bulletPower && !isTeammate(e.getName())) {
//		if (!ramming && fireTime == getTime() && Math.abs(getGunTurnRemainingRadians()) <= Math.atan(36.0 / enemyDistance) && getEnergy() > bulletPower && !isTeammate(e.getName())) {
		if (!ramming && Math.abs(getGunTurnRemainingRadians()) - extraTurn <= Rules.GUN_TURN_RATE_RADIANS && getEnergy() > bulletPower && !isTeammate(e.getName())) {
//		if (!ramming && getEnergy() > 0.1) {
			Bullet b = setFireBullet(bulletPower);
			if (b != null) {
				numFire++;
				realFireTimes.add(velocities.size() - 1);
				// out.println("fired: " + getTime());						
			}
//		}
		}

//		if (Math.abs(getGunTurnRemainingRadians()) - extraTurn > Rules.GUN_TURN_RATE_RADIANS)
//			out.println("wait gun");
	
		setTurnGunRightRadians(Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + lastBearingOffset));
				
		Integer ft = velocities.size() - 1;		
			
		fireTimes5[rn][vIndex1][accelIndex][distanceIndex][wallIndex][lastBearingOffsetIndex].add(ft);
		fireTimes4[rn][vIndex1][accelIndex][distanceIndex][wallIndex].add(ft);
		fireTimes3[rn][vIndex1][accelIndex][distanceIndex].add(ft);
		fireTimes2[rn][vIndex1][accelIndex].add(ft);
		fireTimes1[rn][vIndex1].add(ft);
		fireTimes0[rn].add(ft);

		lastEnemyHeading = e.getHeadingRadians();
		lastEnemyVelocity = enemyVelocity;
		lastEnemyEnergy = e.getEnergy();
		lastMyVelocity = getVelocity();
		_lastEnemyLocation = (Point2D.Double) _enemyLocation.clone();
		
		// Don't need to check whether gun turn will complete in single turn because
    	// we check that gun is finished turning before calling setFire(...).
    	// This is simpler since the precise angle your gun can move in one tick
    	// depends on where your robot is turning.
//    	fireTime = getTime() + 1;
    } // onScannedRobot

	public void onRobotDeath(RobotDeathEvent e) {
		enemyInfos.remove((Object) e.getName());
	}

	private void move(boolean forceSurfing) {
		if (getOthers() <= 1 || forceSurfing)
			doSurfing();
		else
			doMeleeMove();
	}

	private static Point2D.Double destination;
	private static Point2D.Double lastDestination;
	private static double lastAngle;
	private void doMeleeMove() {
//		if (getVelocity() > 0.0) return;
//		if (destination != null && destination.distance(_myLocation) > 20) {
//		if (destination != null && getTime() % 20 != 0 && !(destination.distance(_myLocation) < 1)) {
		if (destination != null && getVelocity() != 0 && !(destination.distance(_myLocation) <= Math.abs(getVelocity()))) {
			goTo(destination);
			return;
		}

		
//		if (getTime() % 10 != 0 && getVelocity() > 0.0) return;
//		if (getDistanceRemaining() > 9.0) return;
/*			
		double destX, destY;
		destX = 24.0 + Math.random() * 300.0;
		destY = 24.0 + Math.random() * (324.0 - destX);
		if (_myLocation.getX() > field.getWidth() / 2.0)
			destX = field.getWidth() - destX;
		if (_myLocation.getY() > field.getHeight() / 2.0)
			destY = field.getHeight() - destY;
		goTo(new Point2D.Double(destX, destY));
*/
		final int numPoints = 300;
		Point2D.Double p, dest = _myLocation;
		double minRisk = Double.POSITIVE_INFINITY;
		//double dist = Math.random() * 20.0 + 160.0;
		double dist = _lastEnemyLocation == null ? Math.random() * 20.0 + 160.0 : Math.max(_lastEnemyLocation.distance(_myLocation) / 3.0, 160.0);
		//double dist = 140.0;
		for (int i = 0; i < numPoints; i++) {
			//double dist = Math.random() * 200.0 + 40.0;
			//double dist = 200.0;
			
			double angle = Math.random() * (Math.PI * 2.0);
			
			p = new Point2D.Double(limit(_moveFieldRect.getX(), _myLocation.getX() + dist * Math.sin(angle),_moveFieldRect.getX() + _moveFieldRect.getWidth()), 
				limit(_moveFieldRect.getY(),_myLocation.getY() + dist * Math.cos(angle),_moveFieldRect.getY() + _moveFieldRect.getHeight()));
			angle = Math.atan2(p.getX() - _myLocation.getX(), p.getY() - _myLocation.getY());
			
			if (Math.abs(Utils.normalRelativeAngle(angle - lastAngle)) < Math.PI / 16.0) continue; // last direction
			if (Math.abs(Utils.normalRelativeAngle(angle - lastAngle + Math.PI)) < Math.PI / 16.0) continue; // last opposite direction			
			//if (!_moveFieldRect.contains(p)) continue;
			//if (p.distance(_myLocation) < 100.0) continue;
			
			double risk = 0.0;
			risk += 300.0 / p.distance(center); // center
			risk += -1 / p.distance(new Point2D.Double(0, 0)); // corners
			risk += -1 / p.distance(new Point2D.Double(0, field.getHeight()));
			risk += -1 / p.distance(new Point2D.Double(field.getWidth(), 0));
			risk += -1 / p.distance(new Point2D.Double(field.getWidth(), field.getHeight()));
			// risk += 50.0 / p.distance(project(_myLocation, lastAngle, dist)); // last direction
			
			Set<Map.Entry<String, EnemyInfo>> infoSet = enemyInfos.entrySet();
			Iterator itr = infoSet.iterator();
			while (itr.hasNext()) {
				Map.Entry me = (Map.Entry) itr.next();
				EnemyInfo enemyInfo = (EnemyInfo) me.getValue();
				Point2D.Double location = enemyInfo.location;
				//risk += enemyInfo.sre.getEnergy() / p.distance(enemyInfo.location);
				risk += 100.0 / p.distance(enemyInfo.location);
				risk += 10.0 / p.distance(_myLocation); // move away from the current location
				if (lastDestination != null) risk += 1.0 / p.distance(lastDestination); // move away from the last destination
			}
			if (risk < minRisk) {
				minRisk = risk;
				dest = p;
			}
			g.setColor(Color.orange);
			g.fillOval((int) (p.getX() - 2), (int) (p.getY() - 2), 4, 4);		
		}
		//if (destination != null) lastDestination = (Point2D.Double) destination.clone();
		lastDestination = (Point2D.Double) _myLocation.clone();
		lastAngle = Math.atan2(dest.getX() - _myLocation.getX(), dest.getY() - _myLocation.getY());
		destination = new Point2D.Double(dest.getX(), dest.getY());
		debugX = dest.getX();
		debugY = dest.getY();
		// out.println(debugX + "," + debugY);
		goTo(destination);		
	}

	static class Wave extends Condition {
		static Point2D targetLocation;

		double bulletPower;
		Point2D gunLocation;
		double bearing;
		double lateralDirection;

		private TeamRobot robot;
		private double distanceTraveled;
		static double guessAngle;
	
		Wave(TeamRobot _robot) {
			this.robot = _robot;
		}
	
		public boolean test() {
			// advance
			distanceTraveled += 20.0 - 3.0 * bulletPower;
			
			// has arrived?
			if (distanceTraveled > gunLocation.distance(targetLocation) - 18) {
				guessAngle = Utils.normalRelativeAngle(absoluteBearing(gunLocation, targetLocation) - bearing) * lateralDirection;
				robot.removeCustomEvent(this);
			}
			return false;
		}

		public double lastBearingOffset() {
			return guessAngle * lateralDirection;
		}
	}																		

	public void onSkippedTurn(SkippedTurnEvent e) {
		System.out.println("Turn skipped at: " + getTime());
	}

	private static double debugX, debugY;
	public void onPaint(java.awt.Graphics2D g) {
//		g.draw(new Rectangle((int) getX() - 18, (int) getY() - 18, 36, 36));
/*		
        EnemyWave surfWave = null;

        for (int x = 0; _enemyWaves != null && x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);

			double eX = ew.fireLocation.getX() + ew.distanceTraveled * Math.sin(ew.directAngle);
			double eY = ew.fireLocation.getY() + ew.distanceTraveled * Math.cos(ew.directAngle);

			g.draw(new Rectangle((int) eX - 8, (int) eY - 8, 16, 16));
			int maxBin = (BINS - 1) / 2;
			for (int i = 0; i < BINS; i++) {
				if (ew.buffer[EnemyWave.BUFFERS - 1][i] > ew.buffer[EnemyWave.BUFFERS - 1][maxBin]) {
					maxBin = i;
				}
			}

			double angle = ew.directAngle + maxEscapeAngle(ew.bulletVelocity) * ew.direction * (double) ((maxBin - (BINS - 1)) / 2) / ((BINS - 1) / 2);
			eX = ew.fireLocation.getX() + ew.distanceTraveled * Math.sin(angle);
			eY = ew.fireLocation.getY() + ew.distanceTraveled * Math.cos(angle);
			g.draw(new Rectangle((int) eX - 4, (int) eY - 4, 8, 8));			
        }
*/		
		// g.draw(new Rectangle((int) debugX - 2, (int) debugY - 2, 4, 4));
		g.setColor(Color.white);
		//g.drawOval((int) (debugX - 25), (int) (debugY - 25), 50, 50);
		if (destination != null) g.drawOval((int) (destination.getX() - 25), (int) (destination.getY() - 25), 50, 50);
		if (lastDestination != null) g.drawLine((int) lastDestination.getX(), (int) lastDestination.getY(), (int) destination.getX(), (int) destination.getY());

		// my position		
		g.setColor(Color.red);
		g.drawOval((int) (getX() - 25), (int) (getY() - 25), 50, 50);
		g.setColor(new Color(0, 0xFF, 0, 30));
		g.fillOval((int) (getX() - 35), (int) (getY() - 35), 70, 70);
		
		// enemies' positions
		Set infosSet = enemyInfos.entrySet();
		Iterator itr = infosSet.iterator();
		while (itr.hasNext()) {
			Map.Entry me = (Map.Entry) itr.next();
			EnemyInfo info = (EnemyInfo) me.getValue();
			String name = (String) me.getKey();

			g.setColor(Color.white);
			g.drawOval((int) (info.location.getX() - 25), (int) (info.location.getY() - 25), 50, 50);
			if (name.compareTo(lastTargetName) == 0) {
				g.setColor(new Color(255, 64, 64, 50));
				g.fillOval((int) (info.location.getX() - 35), (int) (info.location.getY() - 35), 70, 70);
			}
		}
	}

	private Rectangle2D fieldRectangle(double margin) { 
		return new Rectangle2D.Double(margin, margin, getBattleFieldWidth() - margin * 2.0, getBattleFieldHeight() - margin * 2.0);
	}

	private static final int MAX_SAMPLES = 100;	// 50
	// statistical movement reconstructor
	private double bestBearingOffset(ScannedRobotEvent e, double bulletPower, double absBearing, int vIndex1, int vIndex2, int distanceIndex, int wallIndex, int lastBearingOffsetIndex) {
		double angleThreshold = 36.0 / e.getDistance();
//		final double MAX_ESCAPE_ANGLE = maxEscapeAngle(bulletVelocity(bulletPower));
		int binSize = (int) (MAX_ESCAPE_ANGLE * 2.0 / angleThreshold) + 1;
//		final int CLOSEST_SIZE = binSize * 2 + 1;
		final double bulletSpeed = (20.0 - 3.0 * bulletPower);
		final int MIN_SAMPLES = 10;
		
		int rn = robotNameToNum(e.getName());
		Vector ft = fireTimes5[rn][vIndex1][vIndex2][distanceIndex][wallIndex][lastBearingOffsetIndex];
		if (ft.size() < MIN_SAMPLES) {
			ft = fireTimes4[rn][vIndex1][vIndex2][distanceIndex][wallIndex];
			if (ft.size() < MIN_SAMPLES) {
				ft = fireTimes3[rn][vIndex1][vIndex2][distanceIndex];
				if (ft.size() < MIN_SAMPLES) {
					ft = fireTimes2[rn][vIndex1][vIndex2];
					if (ft.size() < MIN_SAMPLES) {
						ft = fireTimes1[rn][vIndex1];
						if (ft.size() < MIN_SAMPLES)
							ft = fireTimes0[rn];
					}
				}
			}
		}
		if (ft.size() == 0) return 0.0;
		//out.println("number of matches: " + ft.size());
/*
		Vector ft = fireTimes3[lastBearingOffsetIndex][distanceIndex][wallIndex];
		if (ft.size() == 0) {
			ft = fireTimes2[lastBearingOffsetIndex][distanceIndex];
			if (ft.size() == 0) {
				ft = fireTimes1[lastBearingOffsetIndex];
				if (ft.size() == 0) {
					ft = fireTimes0;
	
				}
			}
		}	
*/		
		double[] statBin = new double[binSize];
		double metaAngle = 0.0;	
//		System.out.println("binSize: " + binSize + " choices: " + ft.size());

		int maxIndex = 0;		
		int lastIndex = velocities.size() - 1;
		int count = 0;
		final double initialHeading = e.getHeadingRadians();
		final double eV = e.getVelocity();
		
		final double initialEX = enemyDistance * Math.sin(absBearing);
		final double initialEY = enemyDistance * Math.cos(absBearing);
		//double weight = 1.0;
		for (int fi = ft.size() - 1; fi >= 0; fi--) {
			count++;
			if (count > MAX_SAMPLES) break; // limit to recent samples
			int i = ((Integer) ft.get(fi)).intValue();
			double sign = 1.0;
			if (((Double) velocities.get(i)).doubleValue() * enemyDirection < 0)
				sign = -1.0;
		
			// reconstruct enemy movement and find the most popular angle
			double eX = initialEX;
			double eY = initialEY;
			double heading = initialHeading;
			double v = eV;
			double db = 0.0;
			int index = i;
			boolean inField = true;
	
			do {
				db += bulletSpeed;

				eX += v * Math.sin(heading);
				eY += v * Math.cos(heading);
//				eX += v * sign * ((Double) sinHeadingChanges.get(index)).doubleValue();
//				eY += v * ((Double) cosHeadingChanges.get(index)).doubleValue();		
				
				if (!_fieldRect.contains(new Point2D.Double(eX + _myLocation.getX(), eY + _myLocation.getY()))) {
					inField = false;
					break;
				}
				
				//eX = limit(18.0 -_myLocation.getX(), eX, (field.getWidth() - 18.0) - _myLocation.getX());
				//eY = limit(18.0 -_myLocation.getY(), eY, (field.getHeight() -18.0) - _myLocation.getY());
				
				v = sign * ((Double) velocities.get(index)).doubleValue();
				heading += sign * ((Double) headingChanges.get(index)).doubleValue();
				
				if (index + 1 <= lastIndex) {
					index++;
				} /* else {
					inField = false;
					break;
				} */
			//} while (db < Math.hypot(eX, eY));
			} while (db * db < eX * eX + eY * eY); // faster calculation
			g.setColor(Color.YELLOW);
			g.fillOval((int) (_myLocation.getX() + eX - 1), (int) (_myLocation.getY() + eY - 1), 2, 2);
			if (inField) {
//				double pX = limit(18, eX + _myLocation.getX(), 768);
//				double pY = limit(18, eY + _myLocation.getY(), 568);
//				double angle = Utils.normalRelativeAngle(Math.atan2(pX - _myLocation.getX(), pY - _myLocation.getY()) - absBearing);				
				double angle = Utils.normalRelativeAngle(Math.atan2(eX, eY) - absBearing);

				int binIndex = (int) limit(0, ((angle + MAX_ESCAPE_ANGLE) / angleThreshold), binSize - 1);
				//weight /= 1.02; // decay
				double increment = 1.0;
				if (realFireTimes.contains(i)) 
					increment *= 2.0 * fireTimes0[rn].size() / realFireTimes.size();

				statBin[binIndex] += increment;
				if (statBin[binIndex] > statBin[maxIndex]) {
					maxIndex = binIndex;
					metaAngle = angle;
					
					// debugX = eX + _myLocation.getX();
					// debugY = eY + _myLocation.getY();
				}
			}
		}
//	if (realFireTimes.size() > 0)
//   System.out.println(fireTimes0.size() / realFireTimes.size());	
//		System.out.println((double) statBin[maxIndex] / ft.size());
//		if ((double) statBin[maxIndex] / MAX_SAMPLES < 0.25) return Double.POSITIVE_INFINITY;

		return metaAngle;
	}

	// area targeting
	private EnemyInfo selectEnemy() {
		// double angleThreshold = 200.0 / Math.hypot(field.getWidth(), field.getHeight());
		double angleThreshold = 2.0 * MAX_ESCAPE_ANGLE;
//		final double MAX_ESCAPE_ANGLE = maxEscapeAngle(bulletVelocity(bulletPower));
		int binSize = (int) (2.0 * Math.PI / angleThreshold) + 1;
		//out.println(binSize);
		
		double[] statBin = new double[binSize]; // probability of not hitting
		for (int i=0; i < binSize; i++)
			statBin[i] = 1.0;
		EnemyInfo[] metaEnemy = new EnemyInfo[binSize];
		EnemyInfo lastEnemy = (EnemyInfo) enemyInfos.get((Object) lastTargetName);
		int maxIndex = 0;
		if (lastEnemy != null) {
			double _angle = Utils.normalRelativeAngle(Math.atan2(lastEnemy.location.getX() - _myLocation.getX(), lastEnemy.location.getY() - _myLocation.getY()));
			maxIndex = (int) limit(0, (_angle / angleThreshold), binSize - 1);
		}		
					
		LinkedList infosSet = new LinkedList(enemyInfos.values());
		Iterator itr = infosSet.iterator();
		while (itr.hasNext()) {
			EnemyInfo info = (EnemyInfo) itr.next();
			// if (info.sre.getEnergy() == 0.0) return info; // disabled bot
			// if (info.sre.getEnergy() <= 6.0) return info; // to achieve killing bonus
			double angle = Utils.normalRelativeAngle(Math.atan2(info.location.getX() - _myLocation.getX(), info.location.getY() - _myLocation.getY()));

			int binIndex = (int) limit(0, (angle / angleThreshold), binSize - 1);
			double distance = info.location.distance(_myLocation);
			double robotSizeRadians = 36.0 / Math.max(0.0, distance - 18.0);
			double escapeAngle = 2.0 * Math.asin(Rules.getBulletSpeed(3.0));
			double probOfNotHit = 1.0 - Math.min(1.0, robotSizeRadians / escapeAngle);
			//double probOfNotHit = (1.0 - Math.min(1.0, robotSizeRadians / escapeAngle)) * (info.sre.getEnergy() / 100.0);
			//statBin[binIndex] += 1.0 / distance;
			statBin[binIndex] *= probOfNotHit;
			// if (metaEnemy[binIndex] == null || distance < metaEnemy[binIndex].location.distance(_myLocation))
			if (metaEnemy[binIndex] == null || square(distance) < distanceSquare(metaEnemy[binIndex].location, _myLocation)) // faster calculation
				metaEnemy[binIndex] = info;
			if (statBin[binIndex] < statBin[maxIndex]) {
				maxIndex = binIndex;
			}			
		}
		return metaEnemy[maxIndex];																																		
	}

	private static boolean flattening = false;
    public void updateWaves() {
		//flattening = true;
		//if (enemyHitRate < 0.07) flattening = false;
		/*
		if (getTime() % 100 == 0 && enemyHitRate > 0.13) {
			flattening = !flattening;
			out.println("Flattener on: " + flattening);
		}
		*/
        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);

            ew.distanceTraveled = (getTime() - ew.fireTime) * ew.bulletVelocity;
            if (ew.distanceTraveled >
                _myLocation.distance(ew.fireLocation) + 50) {
				if (flattening) {
					// logHit(ew, _myLocation, enemyHitRate * 0.8); // flatten 0.25
					logHit(ew, _myLocation, 0.05);
				}
                _enemyWaves.remove(x);
                x--;
            }
        }
    }
/*
    public EnemyWave getClosestSurfableWave() {
        double closestDistance = Double.POSITIVE_INFINITY;
        EnemyWave surfWave = null;

        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
            double distance = _myLocation.distance(ew.fireLocation)
                - ew.distanceTraveled;

            if (distance > ew.bulletVelocity && distance < closestDistance) {
                surfWave = ew;
                closestDistance = distance;
            }
        }

        return surfWave;
    }
*/

    public EnemyWave getClosestSurfableWave() {
        double closestDistance = Double.POSITIVE_INFINITY;
        EnemyWave surfWave = null;

        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
            double distance = (_myLocation.distance(ew.fireLocation) - ew.distanceTraveled) / ew.bulletVelocity;

            if (distance > 1.0 && distance < closestDistance) {
                surfWave = ew;
                closestDistance = distance;
            }
        }

        return surfWave;
    }


    // Given the EnemyWave that the bullet was on, and the point where we
    // were hit, calculate the index into our stat array for that factor.
    public static int getFactorIndex(EnemyWave ew, Point2D.Double targetLocation) {
        double offsetAngle = (absoluteBearing(ew.fireLocation, targetLocation)
            - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle)
            / maxEscapeAngle(ew.bulletVelocity) * ew.direction;

        return (int)limit(0,
            (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2),
            BINS - 1);
    }

    // Given the EnemyWave that the bullet was on, and the point where we
    // were hit, update our stat array to reflect the danger in that area.
    public void logHit(EnemyWave ew, Point2D.Double targetLocation, double weight) {
        int index = getFactorIndex(ew, targetLocation);
//		weight /= Math.pow(2, getNumRounds() - getRoundNum());
//		weight *= (1.0 + getRoundNum());

		for (int b = 0; b < EnemyWave.BUFFERS; b++) {
	        for (int x = 0; x < BINS; x++) {
            // for the spot bin that we were hit on, add 1;
            // for the bins next to it, add 1 / 2;
            // the next one, add 1 / 5; and so on...
//				double increment = 1.0 / (Math.pow(index - x, 2) + 1);
//				double increment = weight / (Math.pow(index - x, 2) + 0.01);
//				double increment = weight / (Math.pow(index - x, 2) + 0.01);
//				double increment = weight / (Math.pow(2, Math.abs(index - x)));
				double increment = weight / (square(index - x) + 1);				
				ew.buffer[b][x] /= 1.5; // decay 1.5	
            	ew.buffer[b][x] += increment;
			}
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
		enemyHit++;
	
		lastEnemyEnergy += e.getBullet().getPower() * 3;
        // If the _enemyWaves collection is empty, we must have missed the
        // detection of this wave somehow.
        if (!_enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(
                e.getBullet().getX(), e.getBullet().getY());
            EnemyWave hitWave = null;

            // look through the EnemyWaves, and find one that could've hit us.
            for (int x = 0; x < _enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave)_enemyWaves.get(x);

                if (Math.abs(ew.distanceTraveled -
                    _myLocation.distance(ew.fireLocation)) < 50
                    && Math.abs(bulletVelocity(e.getBullet().getPower()) 
                        - ew.bulletVelocity) < 0.001) {
                    hitWave = ew;
                    break;
                }

            }

            if (hitWave != null) {
                logHit(hitWave, hitBulletLocation, 1.0);
				
                // We can remove this wave now, of course.
                _enemyWaves.remove(_enemyWaves.lastIndexOf(hitWave));
            }
        }
    }

    public void onBulletHitBullet(BulletHitBulletEvent e) {
		numBulletHitBullet++;
        // If the _enemyWaves collection is empty, we must have missed the
        // detection of this wave somehow.
    	Bullet bullet = e.getHitBullet();
        if (!_enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(
            		bullet.getX(), bullet.getY());
            EnemyWave hitWave = null;

            // look through the EnemyWaves, and find one that could've hit us.
            for (int x = 0; x < _enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave)_enemyWaves.get(x);

                if (Math.abs(ew.distanceTraveled -
                    _myLocation.distance(ew.fireLocation)) < 50
                    && Math.abs(bulletVelocity(e.getBullet().getPower()) 
                        - ew.bulletVelocity) < 0.001) {
                    hitWave = ew;
                    break;
                }

            }

            if (hitWave != null) {
                logHit(hitWave, hitBulletLocation, 1.0);

                // We can remove this wave now, of course.
                _enemyWaves.remove(_enemyWaves.lastIndexOf(hitWave));
            }
        }
    }

    public void onBulletHit(BulletHitEvent e) {
    	lastEnemyEnergy -= Rules.getBulletDamage(e.getBullet().getPower());
		numHit++;
    }

    public void onHitRobot(HitRobotEvent e) {
    	lastEnemyEnergy -= Rules.ROBOT_HIT_DAMAGE;
		setBackAsFront(this, e.getBearingRadians() + getHeadingRadians());
    }	

    // CREDIT: mini sized predictor from Apollon, by rozu
    // http://robowiki.net?Apollon
    public Point2D.Double predictPosition(EnemyWave surfWave, int direction, double maxVelocity) {
    	Point2D.Double predictedPosition = (Point2D.Double)_myLocation.clone();
    	
    	double predictedVelocity = getVelocity();
    	double predictedHeading = getHeadingRadians();
    	double maxTurning, moveAngle, moveDir;

        int counter = 0;
        boolean intercepted = false;

/*
    	if (direction == 0) { // brake
    		while (predictedVelocity != 0.0) {
    			double nextVelocity = predictedVelocity - Math.signum(predictedVelocity) * 2.0;
    			if (nextVelocity * predictedVelocity < 0.0) nextVelocity = 0.0;
        		predictedVelocity = nextVelocity;
        		// calculate the new predicted position
        		predictedPosition = (Point2D.Double) project(predictedPosition, predictedHeading, predictedVelocity);
   			
    		}
    		return predictedPosition;
    	}
*/

    	do {
// orbit fire location
	
    		moveAngle =
                wallSmoothing(predictedPosition, absoluteBearing(surfWave.fireLocation,
                predictedPosition) + (direction * (FAR_HALF_PI)))
                - predictedHeading;

// orbit enemy location
/*	
    		moveAngle =
                wallSmoothing(predictedPosition, absoluteBearing(_enemyLocation,
                predictedPosition) + (direction * (FAR_HALF_PI)))
                - predictedHeading;
*/
// straight
/*
    		moveAngle =
                wallSmoothing(predictedPosition, absoluteBearing(surfWave.fireLocation,
                surfWave.firstLocation) + (direction * (FAR_HALF_PI)))
                - predictedHeading;
*/

    		moveDir = 1;

    		if(Math.cos(moveAngle) < 0) {
    			moveAngle += Math.PI;
    			moveDir = -1;
    		}

    		moveAngle = Utils.normalRelativeAngle(moveAngle);

    		// maxTurning is built in like this, you can't turn more then this in one tick
    		maxTurning = Math.PI/720d*(40d - 3d*Math.abs(predictedVelocity));
    		predictedHeading = Utils.normalRelativeAngle(predictedHeading
                + limit(-maxTurning, moveAngle, maxTurning));

    		// this one is nice ;). if predictedVelocity and moveDir have
            // different signs you want to breack down
    		// otherwise you want to accelerate (look at the factor "2")
//    		predictedVelocity += (predictedVelocity * moveDir < 0 ? 2*moveDir : moveDir);
//    		predictedVelocity = limit(-maxVelocity, predictedVelocity, maxVelocity);
			if (predictedVelocity * moveDir < 0) {
				double nextVelocity = predictedVelocity + 2 * moveDir;
				if (predictedVelocity * nextVelocity < 0) 
					predictedVelocity = 0.0;
				else
					predictedVelocity = nextVelocity;
			} else if (Math.abs(predictedVelocity) > maxVelocity) {
				double nextVelocity = predictedVelocity - Math.signum(predictedVelocity) * 2.0;
				if (predictedVelocity * nextVelocity < 0) 
					predictedVelocity = 0.0;
				else
					predictedVelocity = nextVelocity;				
			} else {
				predictedVelocity += moveDir;
				predictedVelocity = limit(-maxVelocity, predictedVelocity, maxVelocity);
			}
			

    		// calculate the new predicted position
    		predictedPosition = (Point2D.Double) project(predictedPosition, predictedHeading, predictedVelocity);
			
			// limit
			predictedPosition.x = limit(18, predictedPosition.x, field.getWidth() - 18);
			predictedPosition.y = limit(18, predictedPosition.y, field.getHeight() - 18);

            counter++;

            if (predictedPosition.distance(surfWave.fireLocation) <
                surfWave.distanceTraveled + (counter * surfWave.bulletVelocity)
                + surfWave.bulletVelocity) {
                intercepted = true;
            }
    	} while(!intercepted && counter < 500);

    	return predictedPosition;
    }

    public double checkDanger(EnemyWave surfWave, int direction, double maxVelocity) {
        int index = getFactorIndex(surfWave,
            predictPosition(surfWave, direction, maxVelocity));

        int b = EnemyWave.BUFFERS - 1;
		for (; b >= 0; b--) {
			int count = 0;
	        for (int i = 0; i < BINS; i++) {
				if (surfWave.buffer[b][i] > 0) {
					count++;
					if (count >= 10) return surfWave.buffer[b][index];
				}
/*				if (surfWave.buffer[b][i] > 0) {
//					System.out.println(b);
					return surfWave.buffer[b][index];
				}
*/
	        }
        }
//		System.out.println(0);
        return surfWave.buffer[0][index];
    }
/*
	private double checkDanger2(EnemyWave surfWave, Point2D.Double destination) {
        int index = getFactorIndex(surfWave,
            destination);

        int b = EnemyWave.BUFFERS - 1;
		for (; b >= 0; b--) {
	        for (int i = 0; i < BINS; i++) {
				if (surfWave.buffer[b][i] >= 3) {
//					System.out.println(b);
					return surfWave.buffer[b][index];
				}
	        }
        }
//		System.out.println(0);
        return surfWave.buffer[0][index];		
	}
*/
	private static double goingAngle;
	private static final double FAR_HALF_PI = Math.PI / 2.0; // 1.25
//	private static final double FAR_HALF_PI = 1.25; // 1.25
//	private static final double FAR_HALF_PI = Math.PI - Math.PI / 3.0;
	private static boolean ramming;
	private static double randomDirection = 1.0;
    private void doSurfing() {
//		if (enemyDistance < 250) 
//			FAR_HALF_PI = 1.25;
//		else
//			FAR_HALF_PI = Math.PI / 2.0;
        EnemyWave surfWave = getClosestSurfableWave();

		ramming = false;
        if (surfWave == null) {
			double bulletTravelTime = enemyDistance / Rules.getBulletSpeed(enemyBulletPower);
			if (Math.random() < 0.05 * bulletTravelTime / 25) randomDirection = -randomDirection;	
			setMaxVelocity(8);
			if (_myLocation != null && _enemyLocation != null) {
				if (lastEnemyEnergy <= 0.0) { // ram the disabled enemy
					setBackAsFront(this, absoluteBearing(_myLocation, _enemyLocation));
					ramming = true;
				} else if (!enemyFiredThisRound) { // stay away
					double goAngle = absoluteBearing(_enemyLocation, _myLocation);
					goAngle = wallSmoothing(_myLocation, goAngle - (FAR_HALF_PI / 2.0) * randomDirection);
					setBackAsFront(this, goAngle);
				} else { // random move
//					double goAngle = absoluteBearing(_enemyLocation, _myLocation);
//					goAngle = wallSmoothing(_myLocation, goAngle - (FAR_HALF_PI) * randomDirection, (int) randomDirection);
					double goAngle = getHeadingRadians() * (getVelocity() >= 0.0 ? 1.0 : -1.0);
					goAngle = wallSmoothing(_myLocation, goAngle);
					setBackAsFront(this, goAngle);				
				}
			}
			return;
		}
	
		double bulletTravelTime = _myLocation.distance(surfWave.fireLocation) / surfWave.bulletVelocity;
		if (Math.random() < 0.05 * bulletTravelTime / 25) randomDirection = -randomDirection;
		
        double goAngle = absoluteBearing(surfWave.fireLocation, _myLocation); // orbit fire location
//        double goAngle = absoluteBearing(surfWave.fireLocation, surfWave.firstLocation); // straight
//		double goAngle = absoluteBearing(_enemyLocation, _myLocation); // orbit enemy
		
		double v, vLeft = 8, vRight = 8;

		double dangerLeft = Double.POSITIVE_INFINITY;
        double dangerRight = Double.POSITIVE_INFINITY;
		double cLeft, cRight;
		
		for (v = 8; v >= 0.0; v -= 0.5) {
			cLeft = checkDanger(surfWave, -1, v);
			cRight = checkDanger(surfWave, 1, v);
			
			if (cLeft < dangerLeft) {
				dangerLeft = cLeft;
				vLeft = v;
			}
			if (cRight < dangerRight) {
				dangerRight = cRight;
				vRight = v;
			}		
		}

 //       if (dangerLeft < dangerRight || randomDirection < 0 && dangerLeft == dangerRight) {
		if (dangerLeft < dangerRight) {
            goAngle = wallSmoothing(_myLocation, goAngle - (FAR_HALF_PI));
			goingAngle = goAngle - (FAR_HALF_PI);
 			v = vLeft;
//			if (dangerLeft == dangerRight) System.out.println("asfd");
        } else {
            goAngle = wallSmoothing(_myLocation, goAngle + (FAR_HALF_PI));
			goingAngle = goAngle + (FAR_HALF_PI);
 			v = vRight;
        }

//		System.out.println(v);

		setMaxVelocity(v);
		setBackAsFront(this, goAngle);
    }

    // This can be defined as an inner class if you want.
    static class EnemyWave {
        Point2D.Double fireLocation;
		Point2D.Double firstLocation;
        long fireTime;
        double bulletVelocity, directAngle, distanceTraveled;
        int direction;
		static final int BUFFERS = 8;
		double[][] buffer = new double[BUFFERS][];
		static double[] fastBuffer = new double[BINS];
		private static double MAX_DISTANCE = Math.hypot(1000, 1000);

//        public EnemyWave(double velocity, double lastVelocity, double distance) {
        public EnemyWave(int robotIndex, double velocity, double lastVelocity, double distance, double enemyBulletPower, int myLastExactBearingOffsetIndex) {
            bulletVelocity = bulletVelocity(enemyBulletPower);
			int velocityIndex = (int) Math.abs(velocity / 2);
			int lastVelocityIndex = (int) Math.abs(lastVelocity / 2);
			int distanceIndex = (int)(distance / (MAX_DISTANCE / DISTANCE_INDEXES));
			int wallIndex = !_fieldRect.contains(project(_myLocation, goingAngle, 200)) ? 1 : 0;
			// int wallIndex = wallDistance(_myLocation) < 70.0 ? 0 : 1;
			int powerIndex = (int) enemyBulletPower;
			
			buffer[7] = _surfStats7[robotIndex][velocityIndex][lastVelocityIndex][distanceIndex][wallIndex][myLastExactBearingOffsetIndex];
			buffer[6] = _surfStats6[robotIndex][velocityIndex][lastVelocityIndex][distanceIndex][wallIndex];
			buffer[5] = _surfStats5[robotIndex][velocityIndex][lastVelocityIndex][distanceIndex];
			buffer[4] = _surfStats4[robotIndex][velocityIndex][lastVelocityIndex];
			buffer[3] = _surfStats3[robotIndex][velocityIndex][distanceIndex];
			buffer[2] = _surfStats2[robotIndex][velocityIndex];
			buffer[1] = _surfStats1[robotIndex][distanceIndex];
			buffer[0] = fastBuffer;
		}        
    }

    public double wallSmoothing(Point2D.Double botLocation, double angle) {
		double angle2 = angle;
        while (true) {
			if (_fieldRect.contains(project(botLocation, angle, 160))) { // 160
				return angle;
			} else if (_fieldRect.contains(project(botLocation, angle2, 160))) {
				return angle2;
			}
            angle += 0.05;
			angle2 -= 0.05;
        }
    }

    public static double limit(double min, double value, double max) {
        return Math.max(min, Math.min(value, max));
    }

    public static double bulletVelocity(double power) {
        return (20D - (3D*power));
    }

    public static double maxEscapeAngle(double velocity) {
        return Math.asin(8.0/velocity);
    }
/*
    public static void setBackAsFront(AdvancedRobot robot, double goAngle) {
        double angle =
            Utils.normalRelativeAngle(goAngle - robot.getHeadingRadians());
        if (Math.abs(angle) > (Math.PI/2)) {
            if (angle < 0) {
                robot.setTurnRightRadians(Math.PI + angle);
            } else {
                robot.setTurnLeftRadians(Math.PI - angle);
            }
            robot.setBack(100);
        } else {
            if (angle < 0) {
                robot.setTurnLeftRadians(-1*angle);
           } else {
                robot.setTurnRightRadians(angle);
           }
            robot.setAhead(100);
        }
    }
*/
    public static void setBackAsFront(AdvancedRobot robot, double angle) {
        angle =
            Utils.normalRelativeAngle(angle - robot.getHeadingRadians());		
		double turnAngle = Math.atan(Math.tan(angle));
	    robot.setTurnRightRadians(turnAngle);
        robot.setAhead(angle == turnAngle ? 50 : -50);

		robot.setMaxVelocity(Rules.MAX_VELOCITY);
	}
	
	static Point2D project(Point2D sourceLocation, double angle, double length) {
		return new Point2D.Double(sourceLocation.getX() + Math.sin(angle) * length,
				sourceLocation.getY() + Math.cos(angle) * length);
	}
	
	static double absoluteBearing(Point2D source, Point2D target) {
		return Math.atan2(target.getX() - source.getX(), target.getY() - source.getY());
	}

	private void goTo(Point2D destination) {
		double angle = Utils.normalRelativeAngle(absoluteBearing(_myLocation,
				destination)
				- getHeadingRadians());
		double turnAngle = Math.atan(Math.tan(angle));
		
		setTurnRightRadians(turnAngle);
		setAhead(_myLocation.distance(destination) * (angle == turnAngle ? 1 : -1));
		
		// Hit the brake pedal hard if we need to turn sharply
		// setMaxVelocity(Math.abs(getTurnRemaining()) > 30 ? 1.0 : Rules.MAX_VELOCITY);
		if (wallDistance(_myLocation) < 40.0) {
			double velocity;
			for (velocity = 8.0; velocity >= 1.0; velocity -= 0.5) {
				double maxTurning = Math.PI/720d*(40d - 3d*Math.abs(velocity));
				if (Math.abs(getTurnRemainingRadians()) < maxTurning * 4.0) break;
			}
			setMaxVelocity(velocity);
		} else {
			setMaxVelocity(8.0);
		}
	}
	
	private static double wallDistance(Point2D location) {
		double d = Math.min(location.getX(), field.getWidth() - location.getX());
		d = Math.min(d, Math.min(location.getY(), field.getHeight() - location.getY()));
		return d;
	}

	private static double square(double x) {
		return x * x;
	}

	private static double distanceSquare(Point2D.Double src, Point2D.Double dest) {
		return square(src.x - dest.x) + square(src.y - dest.y);
	}											
}																											
