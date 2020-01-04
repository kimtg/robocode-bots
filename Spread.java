// Spread
// (C) 2010-2020 Kim, Taegyoon

// version 0.1: 20100424 started. K-nearest neighbor play-it-forward, KNN Wavesurfing, Minimum Risk Movement
// version 0.3: 20110717. goto-surfing
// version 0.4: per-robot segmentation in gun and movement
// version 0.5: improve
// version 0.5.1: fixed codesize issue (static block)
// version 0.5.2: tweak

package stelo;

import robocode.*;
import robocode.util.Utils;

import java.awt.geom.*;     // for Point2D's
import java.util.*;
import java.awt.*;

public class Spread extends TeamRobot {
    public static Point2D.Double _myLocation;     // our bot's location

    public ArrayList _enemyWaves;
	
	private static double lateralDirection;

    // This is a rectangle that represents an 800x600 battle field,
    // used for a simple, iterative WallSmoothing method (by Kawigi).
    // If you're not familiar with WallSmoothing, the wall stick indicates
    // the amount of space we try to always have on either end of the tank
    // (extending straight out the front or back) before touching a wall.
//    public static Rectangle2D.Double _fieldRect
//       = new java.awt.geom.Rectangle2D.Double(18, 18, 764, 564);
    public static Rectangle2D.Double _fieldRect
       = new java.awt.geom.Rectangle2D.Double(18, 18, 800-18*2, 600-18*2);
    public static Rectangle2D.Double _moveFieldRect
       = new java.awt.geom.Rectangle2D.Double(30, 30, 800-30*2, 600-30*2);

    private static double lastMyVelocity;

	private static Vector velocities = new Vector();
	private static Vector headingChanges = new Vector();

	private static final int ROBOT_INDEXES = 20;
	private static Vector[] fireTimeLog = new Vector[ROBOT_INDEXES];
			
	private static double MAX_ESCAPE_ANGLE = 0.9;
	private static double lastEnemyHeading = Double.POSITIVE_INFINITY;
	private static double enemyDistance;
	private static double absBearing;
	private static double lastBearingOffset;
	
	private static double enemyFire = 1.0, enemyHit;
	private static double enemyFireNormal = 1.0, enemyHitNormal;
	private static double enemyFireFlat = 1.0, enemyHitFlat;
	private static double numFire, numHit;
	private static double myLastGF;
	private static double enemyLastGF;
	private boolean enemyFiredThisRound;
	private static double enemyVelocity;
	private static double hitRate, enemyHitRate, enemyHitRateNormal, enemyHitRateFlat;
	
	private static double enemyDirection = 1.0;
	private static Rectangle2D.Double field = new java.awt.geom.Rectangle2D.Double(0, 0, 800, 600);
	private static Point2D.Double center = new Point2D.Double(400, 300);
	private static String lastTargetName = "";
	private static long fireTime = 0;
	private static double fireWeight = 1.0;
	private long timeSinceDir = 0;
	private long enemyTimeSinceDir = 0;
	private double dir = 1.0;
	private double lastDir = dir;
	private double enemyDir = 1.0;
	private double lastEnemyDir = enemyDir;
	private static Graphics2D g;
	private double radarDirection = 1;
	private static final int NUM_SAMPLES = 59;
	private static final int NUM_FACTOR = 7;
	
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
		public Point2D.Double lastLocation;
		public double lastVelocity;
  	  	public double lastEnergy;
  	  	public double bulletPower = 0.1;
    	public ArrayList surfDirections;
    	public ArrayList surfAbsBearings;	
		public double lateralMyVelocity;
		public EnemyInfo(ScannedRobotEvent _sre, Point2D.Double _location, Point2D.Double _lastLocation, double _lastVelocity, double _lastEnergy, ArrayList _surfDirections, 
			ArrayList _surfAbsBearings) {
			sre = _sre;
			location = _location;
			firstLocation = (Point2D.Double) location.clone();
			lastLocation = _lastLocation;
			lastVelocity = _lastVelocity;
			lastEnergy = _lastEnergy;
			surfDirections = _surfDirections;
			surfAbsBearings = _surfAbsBearings;
		}
	}
	private EnemyInfo eInfo; // current EnemyInfo
	private static HashMap<String, EnemyInfo> enemyInfos; // for melee
			    
    public void run() {
		g = getGraphics();
		setColors(Color.BLUE, Color.YELLOW, Color.RED);
		setBulletColor(Color.CYAN);
		setScanColor(Color.CYAN);
		lateralDirection = 1;
		
        _enemyWaves = new ArrayList();

		_myLocation = new Point2D.Double(getX(), getY());
		enemyInfos = new HashMap<>();

        setAdjustGunForRobotTurn(true);
//        setAdjustRadarForGunTurn(true);
		setAdjustRadarForRobotTurn(true);
		
		if (getRoundNum() == 0) {
			for (int i = 0; i < ROBOT_INDEXES; i++) {
				fireTimeLog[i] = new Vector();
				EnemyWave.factorLogs[i] = new Vector(10000);
				EnemyWave.factorLogs[i].add(new FactorLog(new double[NUM_FACTOR], 0.0, 1.0)); // GF 0
				EnemyWave.factorLogs[i].add(new FactorLog(new double[NUM_FACTOR], 1.0, 1.0)); // GF 1					
			}
		}

		field = new java.awt.geom.Rectangle2D.Double(0, 0, getBattleFieldWidth(), getBattleFieldHeight());
		_fieldRect = new java.awt.geom.Rectangle2D.Double(18, 18, getBattleFieldWidth() + 1 - 18*2, getBattleFieldHeight() + 1 - 18*2);
		_moveFieldRect = new java.awt.geom.Rectangle2D.Double(30, 30, getBattleFieldWidth() + 1 - 30*2, getBattleFieldHeight() + 1 - 30*2);
		center = new Point2D.Double(field.getWidth() / 2.0, field.getHeight() / 2.0);
		EnemyWave.MAX_DISTANCE = Math.hypot(getBattleFieldWidth(), getBattleFieldHeight());
		enemyInfos.clear();
		
		ramming = false;
		
		if (enemyFire > 0) { // decay
			double decayFactor = enemyFire / 20.0;
			enemyFire /= decayFactor;
			enemyHit /= decayFactor;
		}
		if (enemyFireNormal > 0) { // decay
			double decayFactor = enemyFireNormal / 20.0;
			enemyFireNormal /= decayFactor;
			enemyHitNormal /= decayFactor;
		}
		if (enemyFireFlat > 0) { // decay
			double decayFactor = enemyFireFlat / 20.0;
			enemyFireFlat /= decayFactor;
			enemyHitFlat /= decayFactor;			
		}
		System.out.println("Hit Rate: Me: " + hitRate + "\tEnemy: " + enemyHitRate);
		System.out.println("Enemy Hit Rate Normal: " + enemyHitRateNormal);
		System.out.println("Enemy Hit Rate Flat: " + enemyHitRateFlat);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        do {
            // basic mini-radar code
	        setAdjustRadarForGunTurn(false); // faster radar spin
			
			setTurnGunRightRadians(radarDirection * Double.POSITIVE_INFINITY);
			move(false);
            turnRadarRightRadians(radarDirection * Double.POSITIVE_INFINITY);
        } while (true);
	}

	private void limitRobotLocation(Point2D.Double p) {
		p.x = limit(_fieldRect.x, p.x, _fieldRect.x + _fieldRect.getWidth());
		p.y = limit(_fieldRect.y, p.y, _fieldRect.y + _fieldRect.getHeight());
	}

	public void onStatus(StatusEvent e) {
		_myLocation = new Point2D.Double(getX(), getY());
		updateWaves();

		// interpolate enemy locations
		if (enemyInfos != null) {
			for (Map.Entry<String, EnemyInfo> me: enemyInfos.entrySet()) {
				EnemyInfo ei = me.getValue();
				ei.location.x += ei.sre.getVelocity() * Math.sin(ei.sre.getHeadingRadians());
				ei.location.y += ei.sre.getVelocity() * Math.cos(ei.sre.getHeadingRadians());
				limitRobotLocation(ei.location);
			}
		}		
	}

    public void onScannedRobot(ScannedRobotEvent e) {
		// finding the best target
		absBearing = e.getBearingRadians() + getHeadingRadians();
		Point2D.Double enemyLocation = (Point2D.Double) project(_myLocation, absBearing, e.getDistance());
		int rn = robotNameToNum(e.getName());
		if (!isTeammate(e.getName())) { // update enemy info
			EnemyInfo tInfo = (EnemyInfo) enemyInfos.get((Object) e.getName());
			long lastScan = 0;
			if (tInfo == null) {// new enemy
				enemyInfos.put(e.getName(), new EnemyInfo(e, enemyLocation, enemyLocation, e.getVelocity(), e.getEnergy(), new ArrayList(), new ArrayList()));
				tInfo = (EnemyInfo) enemyInfos.get((Object) e.getName());
			} else {
				lastScan = tInfo.sre.getTime();
				enemyInfos.put(e.getName(), new EnemyInfo(e, enemyLocation, tInfo.location, tInfo.sre.getVelocity(), tInfo.sre.getEnergy(), tInfo.surfDirections, tInfo.surfAbsBearings));
			}
			tInfo.lateralMyVelocity = getVelocity() * Math.sin(e.getBearingRadians());
      	  	tInfo.surfDirections.add(0, new Integer((tInfo.lateralMyVelocity >= 0) ? 1 : -1));
     	   	tInfo.surfAbsBearings.add(0, new Double(absBearing + Math.PI));
			
	        // check energyDrop, adding enemy wave
			{
	    	    double energyDrop = tInfo.lastEnergy - tInfo.sre.getEnergy();
   		    	//if (energyDrop < 3.01 && energyDrop > 0.09 && tInfo.surfDirections.size() > 2) {
				if (energyDrop < 3.01 && energyDrop > 0.09 && tInfo.surfDirections.size() > 2) {
  	      			tInfo.bulletPower = energyDrop;
					enemyFiredThisRound = true;
//              	EnemyWave ew = new EnemyWave();

					EnemyWave surfWave = getClosestSurfableWave();
					if (surfWave != null)
        				myLastGF = getFactor(surfWave, _myLocation);
					double[] info = new double[NUM_FACTOR]; // normalize to [0, 1]
					info[0] = normalize(0.0, Math.abs(this.getVelocity()), 8.0);
					//info[0] = normalize(0.0, Math.abs(lateralVelocity), 8.0);
					info[1] = normalize(0.0, Math.abs(lastMyVelocity), 8.0);
					info[2] = normalize(36.0, _myLocation.distance(tInfo.location), EnemyWave.MAX_DISTANCE); 
					info[3] = normalize(-1.0, myLastGF, 1.0);
					info[4] = normalize(18.0, wallDistance(_myLocation), Math.max(field.getWidth() / 2.0, field.getHeight() / 2.0));
					info[5] = normalize(0.0, Math.abs(tInfo.lateralMyVelocity), 8.0);
					//info[6] = normalize(0.0, e.getName().hashCode(), Integer.MAX_VALUE) * 100.0;					
					//info[6] = normalize(0.0, timeSinceDir, 30.0);
					info[6] = normalize(18.0, cornerDistance(_myLocation), Math.max(field.getWidth() / 2.0, field.getHeight() / 2.0));
					int index = 2;
					if (getOthers() > 1) index = 0;
					/*
					long startT = getTime() - 1 - lastScan;
					long getT = getTime();
					for (long t = startT; t >= 0; t -= 4) {
   	         			EnemyWave ew = new EnemyWave(tInfo.bulletPower, info, (Point2D.Double) tInfo.lastLocation.clone()); // last tick, because that needs the previous enemy location as the source of the wave
   	  		       		ew.fireTime = getT - 2 - t;
						ew.distanceTraveled = bulletVelocity(tInfo.bulletPower);
						ew.direction = ((Integer) tInfo.surfDirections.get(index)).intValue();
						ew.directAngle = ((Double) tInfo.surfAbsBearings.get(index)).doubleValue();
						ew.firstLocation = (Point2D.Double)_myLocation.clone();
	           			_enemyWaves.add(ew);
					}*/
					
   	         			EnemyWave ew = new EnemyWave(rn, tInfo.bulletPower, info, (Point2D.Double) tInfo.lastLocation.clone()); // last tick, because that needs the previous enemy location as the source of the wave
   	  		       		//ew.fireTime = getTime() - 2 - (getTime() - 1 - lastScan);
						ew.fireTime = lastScan - 1;
						ew.distanceTraveled = bulletVelocity(tInfo.bulletPower);
						ew.direction = ((Integer) tInfo.surfDirections.get(index)).intValue();
						ew.directAngle = ((Double) tInfo.surfAbsBearings.get(index)).doubleValue();
						ew.firstLocation = (Point2D.Double)_myLocation.clone();
	           			_enemyWaves.add(ew);
					
					// enemy vs enemy
					/*
					LinkedList infosSet = new LinkedList(enemyInfos.values());
					Iterator itr = infosSet.iterator();
					while (itr.hasNext()) {
						EnemyInfo tInfo2 = (EnemyInfo) itr.next();
						if (tInfo.sre.getName().compareTo(tInfo2.sre.getName()) == 0) { // self
							continue;
						}
						info = new double[6]; // normalize to [0, 1]
						info[0] = normalize(0.0, Math.abs(tInfo2.sre.getVelocity()), 8.0);
						//info[0] = normalize(0.0, Math.abs(lateralVelocity), 8.0);
						info[1] = normalize(0.0, Math.abs(tInfo2.lastVelocity), 8.0);
						info[2] = normalize(36.0, tInfo.location.distance(tInfo2.location), EnemyWave.MAX_DISTANCE); 
						info[3] = normalize(-1.0, 0, 1.0);
						info[4] = normalize(18.0, wallDistance(tInfo2.location), Math.max(field.getWidth() / 2.0, field.getHeight() / 2.0));
						info[5] = normalize(0.0, Math.abs(tInfo2.sre.getVelocity()), 8.0);
						//info[6] = normalize(0.0, timeSinceDir, 30.0);
					
   	         			ew = new EnemyWave(tInfo.bulletPower, info, (Point2D.Double) tInfo.lastLocation.clone()); // last tick, because that needs the previous enemy location as the source of the wave
   	         			ew.fireTime = getTime() - 4;
						ew.distanceTraveled = bulletVelocity(tInfo.bulletPower);
						ew.direction = ((Integer) tInfo.surfDirections.get(index)).intValue();
						ew.directAngle = absoluteBearing(tInfo.location, tInfo2.location);
						ew.firstLocation = (Point2D.Double) tInfo2.location.clone();
	            		_enemyWaves.add(ew);										
					}
					*/
					enemyFire++;
					if (flattening)
						enemyFireFlat++;
					else
						enemyFireNormal++;
   				}
			}				
		} // if
		EnemyInfo lastInfo = (EnemyInfo) enemyInfos.get((Object) lastTargetName);
		eInfo = lastInfo;
		if (eInfo == null) eInfo = (EnemyInfo) enemyInfos.get((Object) e.getName());

		eInfo = selectEnemy();
		if (eInfo == null) eInfo = (EnemyInfo) enemyInfos.get((Object) e.getName());
		e = eInfo.sre;
		enemyLocation = eInfo.location;
		// absBearing = e.getBearingRadians() + getHeadingRadians();
		absBearing = Math.atan2(enemyLocation.getX() - _myLocation.getX(), enemyLocation.getY() - _myLocation.getY());
		
		if (eInfo.sre.getName().compareTo(lastTargetName) != 0) out.println("Target: " + eInfo.sre.getName());
		lastTargetName = eInfo.sre.getName();
		
        setAdjustRadarForGunTurn(true);
//		enemyDistance = e.getDistance();
		enemyDistance = eInfo.location.distance(_myLocation);
		enemyVelocity = e.getVelocity();
		double velocity = getVelocity();
//		double lateralVelocity = velocity * Math.sin(e.getBearingRadians());
		double enemyLateralVelocity = enemyVelocity * Math.sin(e.getBearingRadians());
		
		if (enemyVelocity != 0) enemyDir = Math.signum(enemyVelocity); // enemy direction
		enemyTimeSinceDir++;
		if (enemyDir * lastEnemyDir < 0.0) enemyTimeSinceDir = 0;
		
		if (velocity != 0) dir = Math.signum(velocity); // my direction
		timeSinceDir++;				
		if (dir * lastDir < 0.0) timeSinceDir = 0;
		
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
			if (getGunHeat() < 0.5) { // gun heat lock
				double extraTurn = Math.PI / 4.0;
				double radarTurn = Utils.normalRelativeAngle(absBearing - getRadarHeadingRadians());
				setTurnRadarRightRadians(radarTurn + Math.signum(radarTurn) * extraTurn);
			} else if (enemyInfos.size() == getOthers()) { // oldest scanned
				LinkedList infosSet = new LinkedList(enemyInfos.values());
				Iterator itr = infosSet.iterator();
				long minTime = Long.MAX_VALUE;
				EnemyInfo target = eInfo;
				while (itr.hasNext()) {
					EnemyInfo tInfo = (EnemyInfo) itr.next();
					long t = tInfo.sre.getTime();
					if (t < minTime) {
						minTime = t;
						target = tInfo;
					}
				}
				radarDirection = Utils.normalRelativeAngle(Math.atan2(target.location.x - _myLocation.getX(), target.location.y - _myLocation.getY())
            		- getRadarHeadingRadians());
				setTurnRadarRightRadians(radarDirection * Double.POSITIVE_INFINITY);	
			}	
		}
		{
			velocities.add(new Double(e.getVelocity()));
			if (lastEnemyHeading == Double.POSITIVE_INFINITY) lastEnemyHeading = e.getHeadingRadians();
			double hc = Utils.normalRelativeAngle(e.getHeadingRadians() - lastEnemyHeading);
			headingChanges.add(new Double(hc));
//			sinHeadingChanges.add(new Double(Math.sin(hc)));
//			cosHeadingChanges.add(new Double(Math.cos(hc)));
		}
	
//        _surfDirections.add(0, new Integer((lateralVelocity >= 0) ? 1 : -1));
//        _surfAbsBearings.add(0, new Double(absBearing + Math.PI));
	
		hitRate = numHit / numFire;
		enemyHitRate = enemyHit / enemyFire;
		enemyHitRateNormal = enemyHitNormal / enemyFireNormal;
		enemyHitRateFlat = enemyHitFlat / enemyFireFlat;
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
        //_enemyLocation = (Point2D.Double) project(_myLocation, absBearing, e.getDistance());
		
		double bulletPower = 2.0; // 1.999
		if (getOthers() >= 7) bulletPower = 3.0; // melee
		//double bulletPower = 1.95; // 1.999
		if (getEnergy() < 15.0) bulletPower = 1.0;
		
		// if (getEnergy() < e.getEnergy()) bulletPower = enemyBulletPower / 2.0;
		// if (getOthers() > 7) bulletPower += (double) (getOthers() - 7) * (3.0 - bulletPower) / 3.0;
		// double bulletPower = getOthers() > 7 ? 3.0 : 1.999; // 1.9
		// double bulletPower = limit(1.9, 1.9 + (3.0 - 1.9) / 10.0 * getOthers(), 3.0);
		//bulletPower = Math.min(bulletPower, getEnergy() / 5.0);
		bulletPower = Math.min(bulletPower, getEnergy() - 0.001);
		if (enemyDistance < 50 || numFire > 0 && hitRate > 0.5 && getEnergy() > 50.0) bulletPower = 3.0;
//		if (enemyDistance < 50) bulletPower = 3.0;
//		bulletPower = Math.min(getEnergy() - 0.01, Math.min(bulletPower, e.getEnergy() / 4.0));
		double ee = e.getEnergy();
		//double ee2 = Math.max(0, ee - enemyBulletPower);
		bulletPower = Math.min(bulletPower, ee > 4.0 ? (ee+ 2.0) / 6.0 : ee / 4.0);
		//bulletPower = Math.min(getEnergy() / 4.0, Math.min(bulletPower, ee > 4.0 ? (ee+ 2.0) / 6.0 : ee / 4.0));
		bulletPower = limit(0.1, bulletPower, 3.0);
		
		Wave wave = new Wave(this, Rules.getBulletSpeed(bulletPower));
		wave.gunLocation = new Point2D.Double(getX(), getY());
		Wave.targetLocation = eInfo.location;
		wave.lateralDirection = lateralDirection;
		wave.bulletPower = bulletPower;
		wave.bearing = absBearing;
		addCustomEvent(wave);
		enemyLastGF = Wave.lastGF();
		
		double[] info = new double[NUM_FACTOR];
		info[0] = normalize(0.0, Math.abs(enemyVelocity), 8.0);
		//info[0] = normalize(0.0, Math.abs(enemyLateralVelocity), 8.0);
		info[1] = normalize(0.0, Math.abs(eInfo.lastVelocity), 8.0);
		info[2] = normalize(36.0, enemyDistance, EnemyWave.MAX_DISTANCE); 
		info[3] = normalize(-1.0, enemyLastGF, 1.0);
		info[4] = normalize(18.0, wallDistance(eInfo.location), Math.max(field.getWidth() / 2.0, field.getHeight() / 2.0));
		info[5] = normalize(0.0, Math.abs(enemyLateralVelocity), 8.0);
		//info[6] = normalize(0.0, e.getName().hashCode(), Integer.MAX_VALUE) * 100.0;
		//info[6] = normalize(0.0, enemyTimeSinceDir, 30.0);
		info[6] = normalize(18.0, cornerDistance(eInfo.location), Math.max(field.getWidth() / 2.0, field.getHeight() / 2.0));
		
//		if (e.getEnergy() == 0.0 || (getOthers() > 1 && e.getDistance() > 50))
		if (e.getEnergy() == 0.0) {
			lastBearingOffset = 0.0;
		} else {
			lastBearingOffset = bestBearingOffset(e, bulletPower, absBearing, info);
		}
		
		double extraTurn = Math.atan(18.0 / enemyDistance);
		//fireWeight *= 1.0001;
		fireWeight *= 1.001;
		double ftWeight = fireWeight;		     
//		if (!ramming && getGunHeat() < getGunCoolingRate() * 5.0) {
//		if (!ramming) {
//		if (!ramming && Math.abs(getGunTurnRemainingRadians()) - extraTurn <= Rules.GUN_TURN_RATE_RADIANS && getEnergy() > 0.1) {
//		if (!ramming && fireTime == getTime() && getGunTurnRemaining() == 0 && getEnergy() > bulletPower && !isTeammate(e.getName())) {
		if (!ramming && Math.abs(getGunTurnRemainingRadians()) - extraTurn <= Rules.GUN_TURN_RATE_RADIANS && getEnergy() > bulletPower && !isTeammate(e.getName())) {
//		if (!ramming && getEnergy() > 0.1) {
			Bullet b = setFireBullet(bulletPower);
			if (b != null) {
				numFire++;
				// realFireTimes.add(new Integer(velocities.size() - 1));
				//ftWeight = 22.0;
				ftWeight *= 28.0;				
			}
//		}
		}

//		if (Math.abs(getGunTurnRemainingRadians()) - extraTurn > Rules.GUN_TURN_RATE_RADIANS)
//			out.println("wait gun");
	
		//setTurnGunRightRadians(Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + lastBearingOffset));
		setTurnGunRightRadians(Utils.normalRelativeAngle(absBearing - getGunHeadingRadians() + lastBearingOffset));
		
		int ft = velocities.size() - 1;		
		fireTimeLog[rn].add(new FireTimeLog(info, ft, ftWeight));

		lastEnemyHeading = e.getHeadingRadians();
		//lastEnemyVelocity = enemyVelocity;
		//lastEnemyEnergy = e.getEnergy();
		lastMyVelocity = getVelocity();
		//eInfo.lastLocation = (Point2D.Double) eInfo.location.clone();
		lastDir = dir;
		lastEnemyDir = enemyDir;
		
		// Don't need to check whether gun turn will complete in single turn because
    	// we check that gun is finished turning before calling setFire(...).
    	// This is simpler since the precise angle your gun can move in one tick
    	// depends on where your robot is turning.
    	fireTime = getTime() + 1;
    } // onScannedRobot

	public void onRobotDeath(RobotDeathEvent e) {
		enemyInfos.remove((Object) e.getName());
	}

	private void move(boolean forceSurfing) {
		
		if (getOthers() <= 1 || forceSurfing)
			doSurfing();
		else {
			doMeleeMove();
		}
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
		//double dist = eInfo == null || eInfo.lastLocation == null ? Math.random() * 20.0 + 160.0 : Math.max(eInfo.lastLocation.distance(_myLocation) / 3.0, 160.0);
		//double dist = 140.0;
		double surfAngle = doSurfingMelee();
		for (int i = 0; i < numPoints; i++) {
			//double dist = Math.random() * 200.0 + 40.0;
			//double dist = 200.0;
			double dist = Math.random() * 160.0 + 20.0;
			
			double angle = Math.random() * (Math.PI * 2.0);
			boolean angleRejected = false;
			
			p = new Point2D.Double(limit(_moveFieldRect.getX(), _myLocation.getX() + dist * Math.sin(angle),_moveFieldRect.getX() + _moveFieldRect.getWidth()), 
				limit(_moveFieldRect.getY(),_myLocation.getY() + dist * Math.cos(angle),_moveFieldRect.getY() + _moveFieldRect.getHeight()));
			angle = Math.atan2(p.getX() - _myLocation.getX(), p.getY() - _myLocation.getY());
			
			if (Math.abs(Utils.normalRelativeAngle(angle - lastAngle)) < Math.PI / 16.0) continue; // last direction
			if (Math.abs(Utils.normalRelativeAngle(angle - lastAngle + Math.PI)) < Math.PI / 16.0) continue; // last opposite direction			
			//if (!_moveFieldRect.contains(p)) continue;
			//if (p.distance(_myLocation) < 100.0) continue;
			
			double risk = 0.0;
			risk += 300.0 / p.distance(center); // center
			// risk += 50.0 / p.distance(project(_myLocation, lastAngle, dist)); // last direction
			
			Set infoSet = enemyInfos.entrySet();
			Iterator itr = infoSet.iterator();
			while (itr.hasNext()) {
				Map.Entry me = (Map.Entry) itr.next();
				EnemyInfo enemyInfo = (EnemyInfo) me.getValue();
				//risk += enemyInfo.sre.getEnergy() / p.distance(enemyInfo.location);
				risk += 100.0 / p.distance(enemyInfo.location);
				risk += 10.0 / p.distance(_myLocation); // move away from the current location
				if (lastDestination != null) risk += 1.0 / p.distance(lastDestination); // move away from the last destination
			}
			/*
			// head-on bullet
			double bulletTime = p.distance(_myLocation) / 8;
			for (int x = 0; x < _enemyWaves.size(); x++) {
            	EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
				double distance = ew.distanceTraveled + bulletTime * ew.bulletVelocity;
				Point2D.Double projectedBulletLocation = new Point2D.Double(ew.fireLocation.getX() + distance * Math.sin(ew.directAngle),
					ew.fireLocation.getY() + distance * Math.cos(ew.directAngle));
					
				if (p.distance(projectedBulletLocation) < 36) {
					//risk += Double.POSITIVE_INFINITY;
					risk += 0.1;
					//risk += 10.0;
					//break;
				}
				double tAngle = absoluteBearing(_myLocation, projectedBulletLocation);
				if (Math.abs(Utils.normalRelativeAngle(angle - tAngle)) < Math.PI / 32.0 ||
					Math.abs(Utils.normalRelativeAngle(angle - tAngle + Math.PI)) < Math.PI / 32.0) {
						//risk += Double.POSITIVE_INFINITY;
						risk += 0.1;
						//risk += 10.0;
						//break;
				}
				
				//risk += 100.0 / p.distance(bulletLocation);
				
				// risk += 100.0 / p.distance(bulletLocation);								
			} */
			// wave surfing angle
			if (Math.abs(Utils.normalRelativeAngle(angle - surfAngle)) < Math.PI / 32.0 ||
				Math.abs(Utils.normalRelativeAngle(angle - surfAngle + Math.PI)) < Math.PI / 32.0) {
				risk /= 1.1;
			}
			
			//if (risk == Double.POSITIVE_INFINITY) continue;
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
		static double lastAngle;
		static double lastGuessFactor;
		private double maxEscapeAngle;
	
		Wave(TeamRobot _robot, double _bulletVelocity) {
			this.robot = _robot;
			maxEscapeAngle = maxEscapeAngle(_bulletVelocity);
		}
	
		public boolean test() {
			// advance
			distanceTraveled += 20.0 - 3.0 * bulletPower;
			
			// has arrived?
			if (distanceTraveled > gunLocation.distance(targetLocation) - 18) {
				lastAngle = Utils.normalRelativeAngle(absoluteBearing(gunLocation, targetLocation) - bearing);
				lastGuessFactor = limit(1.0, lastAngle / maxEscapeAngle * lateralDirection, 1.0);
				robot.removeCustomEvent(this);
			}
			return false;
		}

		public static double lastGF() {
			return lastGuessFactor;
		}
	}																		
/*
	public void onSkippedTurn(SkippedTurnEvent e) {
		System.out.println("Turn skipped at: " + getTime());
	}
*/
	public void onHitWall(HitWallEvent e) {
		out.println("Hit a wall: " + getTime());
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
		// gun heat
		//g.drawString("Gun heat: " + getGunHeat(), 10, 10);
		g.drawString("Velocity: " + getVelocity(), 10, 10);
				
		// melee move
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
	
		// enemy wave
		{
			long time = getTime();
	        for (int x = 0; x < _enemyWaves.size(); x++) {
	            EnemyWave ew = (EnemyWave) _enemyWaves.get(x);
				double d2 = ew.distanceTraveled * 2.0;
				g.setColor(Color.gray);
				// g.drawOval((int) (ew.fireLocation.getX() - 2), (int) (ew.fireLocation.getY() - 2),(int) 4,(int) 4);
				g.drawOval((int) (ew.fireLocation.getX() - ew.distanceTraveled), (int) (ew.fireLocation.getY() - ew.distanceTraveled),(int) d2,(int) d2);
				g.setColor(Color.blue);
				g.drawLine((int) ew.fireLocation.getX(), (int) ew.fireLocation.getY(), 
					(int) (ew.fireLocation.getX() + ew.distanceTraveled * Math.sin(ew.directAngle)), (int) (ew.fireLocation.getY() + ew.distanceTraveled * Math.cos(ew.directAngle)));
			}
		}	
	}

	private Rectangle2D fieldRectangle(double margin) { 
		return new Rectangle2D.Double(margin, margin, getBattleFieldWidth() - margin * 2.0, getBattleFieldHeight() - margin * 2.0);
	}

	// statistical movement reconstructor
	private double bestBearingOffset(ScannedRobotEvent e, double bulletPower, double absBearing, double[] info) {
		final double angleThreshold = Math.atan(36.0 / e.getDistance()); // 18.0
		// final double angleThreshold = Math.atan(1.0 / e.getDistance()); // pixel accuracy
		final double maxEscapeAngle = maxEscapeAngle(bulletVelocity(bulletPower));
		final int binSize = (int) (maxEscapeAngle * 2.0 / angleThreshold) + 1;
//		final int CLOSEST_SIZE = binSize * 2 + 1;
		final double bulletSpeed = (20.0 - 3.0 * bulletPower);
		
		int rn = robotNameToNum(e.getName());
		final int NUM_NEAREST = Math.min(NUM_SAMPLES, fireTimeLog[rn].size());
		FireTimeLog[] nearestLogs = new FireTimeLog[NUM_NEAREST];
		double[] diffs = new double[NUM_NEAREST];
		{ // find k-nearest neighbors
			int maxIndex = 0;
			for (int i = 0; i < NUM_NEAREST; i++) {
				diffs[i] = Double.POSITIVE_INFINITY;
			}
		
			// fill nearestLogs
			for (int i = fireTimeLog[rn].size() - 1; i >=0; i--) {
				FireTimeLog fl = (FireTimeLog) fireTimeLog[rn].get(i);
				final double d = difference(info, fl.info);
				if (d < diffs[maxIndex]) {
					diffs[maxIndex] = d;
					nearestLogs[maxIndex] = fl;
					for (int j = 0; j < NUM_NEAREST; j++) {
						if (diffs[j] > diffs[maxIndex]) maxIndex = j;
					} 	
				}
			}
		}		
			
		double[] statBin = new double[binSize];
		double[] metaAngle = new double[binSize];	
//		System.out.println("binSize: " + binSize + " choices: " + ft.size());

		int maxIndex = 0;		
		final int lastIndex = velocities.size() - 1;
		
		final double initialHeading = e.getHeadingRadians();
		final double eV = e.getVelocity();
		
		final double initialEX = enemyDistance * Math.sin(absBearing);
		final double initialEY = enemyDistance * Math.cos(absBearing);
		for (int fi = 0; fi < NUM_NEAREST; fi++) {
			if (nearestLogs[fi] == null) continue;
			final int i = nearestLogs[fi].fireTime;
			double sign = 1.0;
			if (((Double) velocities.get(i)).doubleValue() * enemyDirection < 0)
				sign = -1.0;
		
			// reconstruct enemy movement and find the most popular angle
			double eX = initialEX;
			double eY = initialEY;
			double heading = initialHeading;
			double v = eV;
			double db = 0;
			int index = i;
			boolean inField = true;
	
			do {
				db += bulletSpeed;

				eX += v * Math.sin(heading);
				eY += v * Math.cos(heading);		
				
				if (!_fieldRect.contains(new Point2D.Double(eX + _myLocation.getX(), eY + _myLocation.getY()))) {
					inField = false;
					break;
				}
				
				//eX = limit(-_myLocation.getX(), eX, field.getWidth() - _myLocation.getX());
				//eY = limit(-_myLocation.getY(), eY, field.getHeight() - _myLocation.getY());
				
				v = sign * ((Double) velocities.get(index)).doubleValue();
				heading += sign * ((Double) headingChanges.get(index)).doubleValue();
				
				if (index + 1 <= lastIndex) {
					index++;
				} else {
					inField = false;
					break;
				}
			//} while (db < Math.hypot(eX, eY));
			} while (db * db < eX * eX + eY * eY); // faster calculation
		
			if (inField) {
				g.setColor(Color.YELLOW);
				g.fillOval((int) (_myLocation.getX() + eX - 1), (int) (_myLocation.getY() + eY - 1), 2, 2);
//				double pX = limit(18, eX + _myLocation.getX(), 768);
//				double pY = limit(18, eY + _myLocation.getY(), 568);
//				double angle = Utils.normalRelativeAngle(Math.atan2(pX - _myLocation.getX(), pY - _myLocation.getY()) - absBearing);				
				double angle = Utils.normalRelativeAngle(Math.atan2(eX, eY) - absBearing);

				int binIndex = (int) limit(0, ((angle + maxEscapeAngle) / angleThreshold), binSize - 1);
				metaAngle[binIndex] = angle;
				
				statBin[binIndex] += nearestLogs[fi].weight / diffs[fi];
				if (statBin[binIndex] > statBin[maxIndex]) {
					maxIndex = binIndex;
				}
				
				/*
	   		    for (int x = 0; x < binSize; x++) { // bin smoothing
					double increment = nearestLogs[fi].weight / (square(binIndex - x) + 1);
    		      	statBin[x] += increment;
				}*/
			}
		}

/*
		final int middleIndex = (binSize - 1) / 2;
		maxIndex = middleIndex;
		for (int x = 0; x < binSize; x++) {
			if (statBin[x] > statBin[maxIndex])
				maxIndex = x;
		}
		return (double) (maxIndex - middleIndex) / binSize * 2.0 * maxEscapeAngle;*/
		return metaAngle[maxIndex];
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
			double angle = Utils.normalRelativeAngle(Math.atan2(lastEnemy.location.getX() - _myLocation.getX(), lastEnemy.location.getY() - _myLocation.getY()));
			maxIndex = (int) limit(0, (angle / angleThreshold), binSize - 1);
		}		
					
		LinkedList infosSet = new LinkedList(enemyInfos.values());
		Iterator itr = infosSet.iterator();
		while (itr.hasNext()) {
			EnemyInfo info = (EnemyInfo) itr.next();
			if (info.sre.getEnergy() == 0.0) return info; // disabled bot
			// if (info.sre.getEnergy() <= 6.0) return info; // to achieve killing bonus
			double angle = Utils.normalRelativeAngle(Math.atan2(info.location.getX() - _myLocation.getX(), info.location.getY() - _myLocation.getY()));

			int binIndex = (int) limit(0, (angle / angleThreshold), binSize - 1);
			double distance = info.location.distance(_myLocation);
			double robotSizeRadians = 36.0 / Math.max(0.0, distance - 18.0);
			double escapeAngle = 2.0 * Math.asin(Rules.getBulletSpeed(3.0));
			double probOfNotHit = 1.0 - Math.min(1.0, robotSizeRadians / escapeAngle);
			//double probOfNotHit = (1.0 - Math.min(1.0, robotSizeRadians / escapeAngle)) * Math.sqrt(info.sre.getEnergy() / 100.0);
			//statBin[binIndex] += 1.0 / distance;
			statBin[binIndex] *= probOfNotHit;
			// if (metaEnemy[binIndex] == null || distance < metaEnemy[binIndex].location.distance(_myLocation))
			if (metaEnemy[binIndex] == null || square(distance) < distanceSquare(metaEnemy[binIndex].location, _myLocation)) // faster calculation
				metaEnemy[binIndex] = info;
			if (statBin[binIndex] < statBin[maxIndex] * 0.8) {
				maxIndex = binIndex;
			}			
		}
		return metaEnemy[maxIndex];																																		
	}

	private static boolean flattening = false;
//	private static boolean flattening = true;
	private static boolean lastFlattening = false;
    public void updateWaves() {
		if (_enemyWaves == null) return;
		//flattening = false;
		//if (enemyHitRate > 0.10) flattening = true;
		//if (enemyHitRate < 0.07) flattening = false;
/*		if (getOthers() <= 1 && enemyHitRateNormal > enemyHitRateFlat * 1.2)
			flattening = true;
		else 
			flattening = false;
*/			
		if (flattening != lastFlattening)
			out.println("Flattener on: " + flattening);
		/*
		if (getTime() % 100 == 0 && enemyHitRate > 0.13) {
			flattening = !flattening;
			out.println("Flattener on: " + flattening);
		}
		*/
		long time = getTime();
        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave) _enemyWaves.get(x);

            ew.distanceTraveled = (time - ew.fireTime) * ew.bulletVelocity;
            // if (ew.distanceTraveled > _myLocation.distance(ew.fireLocation) + 50) {
			if (ew.distanceTraveled > _myLocation.distance(ew.fireLocation)) {
				if (flattening) {
					logHitFlat(ew, _myLocation, 1.0);
				}
				// logHit(ew, _myLocation, 0.1);
				_enemyWaves.remove(x);
                x--;
            }
        }
		lastFlattening = flattening;
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

    public EnemyWave getClosestSurfableWave2(EnemyWave closestWave) {
		if (closestWave == null) return null;
        double closestDistance = Double.POSITIVE_INFINITY;
        EnemyWave surfWave = null;
		
        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
            double distance = (_myLocation.distance(ew.fireLocation) - ew.distanceTraveled) / ew.bulletVelocity;

            if (ew != closestWave && distance > 1.0 && distance < closestDistance) {
                surfWave = ew;
                closestDistance = distance;
            }
        }
		
        return surfWave;
    }

    // Given the EnemyWave that the bullet was on, and the point where we
    // were hit, calculate the guess factor
/*
    public static int getFactorIndex(EnemyWave ew, Point2D.Double targetLocation) {
        double offsetAngle = (absoluteBearing(ew.fireLocation, targetLocation)
            - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle)
            / maxEscapeAngle(ew.bulletVelocity) * ew.direction;

        return (int)limit(0,
            (factor * ((BINS - 1) / 2)) + ((BINS - 1) / 2),
            BINS - 1);
    }
*/
    public static double getFactor(EnemyWave ew, Point2D.Double targetLocation) {
        double offsetAngle = (absoluteBearing(ew.fireLocation, targetLocation)
            - ew.directAngle);
        double factor = Utils.normalRelativeAngle(offsetAngle)
            / maxEscapeAngle(ew.bulletVelocity) * ew.direction;
		//return factor;
		return limit(-1.0, factor, 1.0);
    }

	public static int factorIndex(EnemyWave ew, double factor) {
		return (int) (normalize(-1.0, factor, 1.0) * (ew.buffer.length - 1));
	}

    // Given the EnemyWave that the bullet was on, and the point where we
    // were hit, update our stat array to reflect the danger in that area.
	private static double globalFactorLogWeight = 1.0;
    public void logHit(EnemyWave ew, Point2D.Double targetLocation, double weight) {
		double factor = getFactor(ew, targetLocation);
//		out.println("loghit: " + factor);
		globalFactorLogWeight *= 1.01;
		ew.factorLog.add(new FactorLog(ew.info, factor, weight * globalFactorLogWeight));
		
		if (getOthers() <= 1) {
        	for (int x = 0; x < _enemyWaves.size(); x++) {
           		EnemyWave ew2 = (EnemyWave) _enemyWaves.get(x);
				ew2.updateBuffer();
			}
		}	
    }

    public void logHitFlat(EnemyWave ew, Point2D.Double targetLocation, double weight) {
		double factor = getFactor(ew, targetLocation);
		globalFactorLogWeight *= 1.01;
		ew.factorLogFlat.add(new FactorLog(ew.info, factor, weight * globalFactorLogWeight));
		
        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew2 = (EnemyWave) _enemyWaves.get(x);
			ew2.updateBuffer();
		}		
    }

    public void onHitByBullet(HitByBulletEvent e) {
		enemyHit++;
		if (flattening)
			enemyHitFlat++;
		else
			enemyHitNormal++;
		
		EnemyInfo tInfo = (EnemyInfo) enemyInfos.get((Object) e.getName());
		if (tInfo != null) tInfo.lastEnergy += e.getBullet().getPower() * 3;
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
        // If the _enemyWaves collection is empty, we must have missed the
        // detection of this wave somehow.
    	Bullet bullet = e.getHitBullet();
        if (!_enemyWaves.isEmpty()) {
            Point2D.Double hitBulletLocation = new Point2D.Double(bullet.getX(), bullet.getY());
            EnemyWave hitWave = null;

            // look through the EnemyWaves, and find one that could've hit us.
            for (int x = 0; x < _enemyWaves.size(); x++) {
                EnemyWave ew = (EnemyWave)_enemyWaves.get(x);

                if (Math.abs(ew.distanceTraveled -
                    hitBulletLocation.distance(ew.fireLocation)) < 20
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
		EnemyInfo tInfo = (EnemyInfo) enemyInfos.get((Object) e.getName());
		if (tInfo != null) tInfo.lastEnergy -= Rules.getBulletDamage(e.getBullet().getPower());
		numHit++;
    }

    public void onHitRobot(HitRobotEvent e) {
		EnemyInfo tInfo = (EnemyInfo) enemyInfos.get((Object) e.getName());
    	if (tInfo != null) tInfo.lastEnergy -= Rules.ROBOT_HIT_DAMAGE;
		setBackAsFront(this, e.getBearingRadians() + getHeadingRadians()); // ram
    }	

    // CREDIT: mini sized predictor from Apollon, by rozu
    // http://robowiki.net?Apollon
    public ArrayList<Point2D.Double> predictPositions(EnemyWave surfWave, int direction) {
		final double maxVelocity = 8.0;
		ArrayList<Point2D.Double> predictedPositions = new ArrayList();
    	Point2D.Double predictedPosition = (Point2D.Double)_myLocation.clone();
    	
    	double predictedVelocity = getVelocity();
    	double predictedHeading = getHeadingRadians();
    	double maxTurning, moveAngle, moveDir;

        int counter = 0;
        boolean intercepted = false;

    	do {
// orbit fire location
/*	
    		moveAngle =
                wallSmoothing(predictedPosition, absoluteBearing(surfWave.fireLocation,
                predictedPosition) + (direction * (FAR_HALF_PI)), direction)
                - predictedHeading;
*/

// orbit enemy location
/*	
    		moveAngle =
                wallSmoothing(predictedPosition, absoluteBearing(_enemyLocation,
                predictedPosition) + (direction * (FAR_HALF_PI)))
                - predictedHeading;
*/
// straight

    		moveAngle =
                wallSmoothing(predictedPosition, absoluteBearing(surfWave.fireLocation, surfWave.firstLocation) + (direction * (FAR_HALF_PI)), direction)
                - predictedHeading;


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
			predictedPositions.add(predictedPosition);
			// limit
//			predictedPosition.x = limit(18, predictedPosition.x, field.getWidth() - 18);
//			predictedPosition.y = limit(18, predictedPosition.y, field.getHeight() - 18);

            counter++;

            if (predictedPosition.distance(surfWave.fireLocation) <
                surfWave.distanceTraveled + (counter * surfWave.bulletVelocity)
                + surfWave.bulletVelocity) {
                intercepted = true;
            }
			g.setColor(Color.WHITE);
			g.fillOval((int) (predictedPosition.getX() - 1), (int) (predictedPosition.getY() - 1), 2, 2);
	    } while(!intercepted && counter < 500);
		
    	return predictedPositions;
    }

    public Point2D.Double predictPositionAngle(EnemyWave surfWave, double angle, double maxVelocity) {
    	Point2D.Double predictedPosition = (Point2D.Double)_myLocation.clone();
    	
    	double predictedVelocity = getVelocity();
    	double predictedHeading = getHeadingRadians();
    	double maxTurning, moveAngle, moveDir;

        int counter = 0;
        boolean intercepted = false;
		double surfWaveTraveled = surfWave.distanceTraveled + surfWave.bulletVelocity;

    	do {
    		moveAngle = wallSmoothing(predictedPosition, angle) - predictedHeading;
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
			//predictedPosition.x = limit(18, predictedPosition.x, field.getWidth() - 18);
			//predictedPosition.y = limit(18, predictedPosition.y, field.getHeight() - 18);

            counter++;
			surfWaveTraveled += surfWave.bulletVelocity;
/*			
            if (predictedPosition.distance(surfWave.fireLocation) <
                surfWave.distanceTraveled + (counter * surfWave.bulletVelocity) + surfWave.bulletVelocity) {
                intercepted = true;
            }
*/
            if (predictedPosition.distanceSq(surfWave.fireLocation) < surfWaveTraveled * surfWaveTraveled) {
                intercepted = true;
            }
		
    	} while(!intercepted && counter < 500);

    	return predictedPosition;
    }						

	public double checkDanger(EnemyWave surfWave, Point2D.Double position) {
        double factor = getFactor(surfWave, position);
		int index = factorIndex(surfWave, factor);
		double distance = position.distance(surfWave.fireLocation) - surfWave.distanceTraveled;
		return surfWave.buffer[index]/distance;
	}
/*
    public double checkDanger(EnemyWave surfWave, int direction, double maxVelocity) {
        double factor = getFactor(surfWave, predictPosition(surfWave, direction, maxVelocity));

		int index = factorIndex(surfWave, factor);
		
		return surfWave.buffer[index];
    }
*/

    public double checkDangerAngleAll(EnemyWave surfWave, double angle, double maxVelocity) {
		Point2D.Double position = predictPositionAngle(surfWave, angle, maxVelocity);
        double factor = getFactor(surfWave, position);

		int index = factorIndex(surfWave, factor);
		double danger = surfWave.buffer[index];
		double distanceClosest = (_myLocation.distance(surfWave.fireLocation) - surfWave.distanceTraveled) / surfWave.bulletVelocity;
		
        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
			double distance = (_myLocation.distance(ew.fireLocation) - ew.distanceTraveled) / ew.bulletVelocity;	
			if (ew != surfWave && Math.abs(distance - distanceClosest) < 10) {
				index = factorIndex(ew, getFactor(ew, position));
				danger += ew.buffer[index];
			}
		}
		
		return danger;
    }
	
    public double checkDangerAll(EnemyWave surfWave, Point2D.Double position) {
		double factor = getFactor(surfWave, position);
		int index = factorIndex(surfWave, factor);
		double danger = surfWave.buffer[index];
		double distanceClosest = (_myLocation.distance(surfWave.fireLocation) - surfWave.distanceTraveled) / surfWave.bulletVelocity;
		
        for (int x = 0; x < _enemyWaves.size(); x++) {
            EnemyWave ew = (EnemyWave)_enemyWaves.get(x);
			double distance = (_myLocation.distance(ew.fireLocation) - ew.distanceTraveled) / ew.bulletVelocity;	
			if (ew != surfWave && Math.abs(distance - distanceClosest) < 10) {
				index = factorIndex(ew, getFactor(ew, position));
				danger += ew.buffer[index];
			}
		}
		
		return danger;
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
	private EnemyWave lastSurfWave;
	private int lastSurfDir;
	
    private void doSurfing() {
//		if (enemyDistance < 250) 
//			FAR_HALF_PI = 1.25;
//		else
//			FAR_HALF_PI = Math.PI / 2.0;
        EnemyWave surfWave = getClosestSurfableWave();
		
		if (surfWave != null && surfWave != lastSurfWave)
			surfWave.firstLocation = (Point2D.Double) _myLocation.clone();

		ramming = false;
		/*
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
					goAngle = wallSmoothing(_myLocation, goAngle - (FAR_HALF_PI / 2.0) * 
randomDirection);
					setBackAsFront(this, goAngle);
				} else { // random move
//					double goAngle = absoluteBearing(_enemyLocation, _myLocation);
//					goAngle = wallSmoothing(_myLocation, goAngle - (FAR_HALF_PI) * 
randomDirection, (int) randomDirection);
					double goAngle = getHeadingRadians() * (getVelocity() >= 0.0 ? 1.0 : -1.0);
					goAngle = wallSmoothing(_myLocation, goAngle);
					setBackAsFront(this, goAngle);				
				}
			}
			return;
		} */
	
        if (surfWave == null) {	
			setMaxVelocity(8);
			if (_myLocation != null && eInfo != null && eInfo.location != null) {
				if (eInfo.sre.getEnergy() <= 0.0) { // ram the disabled enemy
					setBackAsFront(this, absoluteBearing(_myLocation, eInfo.location));
					ramming = true;
					return;
//					return 0;
				}
				//setTurnRightRadians(Utils.normalRelativeAngle(FAR_HALF_PI + absoluteBearing(_myLocation, _enemyLocation) - getHeadingRadians()));
			}
			return;
//			return 0;
		}
	
		double bulletTravelTime = _myLocation.distance(surfWave.fireLocation) / surfWave.bulletVelocity;
		if (Math.random() < 0.05 * bulletTravelTime / 25) randomDirection = -randomDirection;
		
//        double goAngle = absoluteBearing(surfWave.fireLocation, _myLocation); // orbit fire location
        double goAngle = absoluteBearing(surfWave.fireLocation, surfWave.firstLocation); // straight
//		double goAngle = absoluteBearing(_enemyLocation, _myLocation); // orbit enemy
		
		double v, vLeft = 8, vRight = 8;

		double dangerLeft = Double.POSITIVE_INFINITY;
        double dangerRight = Double.POSITIVE_INFINITY;
		double cLeft, cRight;
		
//		for (v = 8; v >= 0; v -= 1) {
/*	
		for (v = 0; v <= 8; v += 1) {
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
*/
		ArrayList<Point2D.Double> positionsLeft = predictPositions(surfWave, -1);
		ArrayList<Point2D.Double> positionsRight = predictPositions(surfWave, 1);
		Point2D.Double bestPositionLeft = new Point2D.Double();
		Point2D.Double bestPositionRight = new Point2D.Double();
		Point2D.Double bestPosition;
		
		for (int i = 0; i < positionsLeft.size(); i++) {
			Point2D.Double p = positionsLeft.get(i);
			double danger = checkDanger(surfWave, p);
			if (danger < dangerLeft) {
				dangerLeft = danger;
				bestPositionLeft = p;
			}
		}
		for (int i = 0; i < positionsRight.size(); i++) {
			Point2D.Double p = positionsRight.get(i);
			double danger = checkDanger(surfWave, p);
			if (danger < dangerRight) {
				dangerRight = danger;
				bestPositionRight = p;
			}
		}	

		int surfDir = 0;
		if (dangerLeft < dangerRight) {
            surfDir = 0;
        } else if (dangerLeft == dangerRight) {
			surfDir = lastSurfDir;
		} else {
            surfDir = 1;
        }
		
		if (surfDir == 0) { // left
			bestPosition = bestPositionLeft;
		} else {
			bestPosition = bestPositionRight;
		}			

		goTo(bestPosition);
		g.setColor(Color.GREEN);			
		g.fillOval((int) (bestPosition.getX() - 4), (int) (bestPosition.getY() - 4), 8, 8);
			
		// System.out.println(v);
//		setBackAsFront(this, goAngle);
//		setMaxVelocity(v);
		lastSurfWave = surfWave;
		lastSurfDir = surfDir;
//		return goAngle;
    }
	
	private Point2D.Double nearestCorner(Point2D.Double p) {
		double x = 18.0, y = 18.0;
		if (p.x > getBattleFieldWidth() / 2) x = getBattleFieldWidth() - x;
		if (p.y > getBattleFieldHeight() / 2) x = getBattleFieldHeight() - y;
		return new Point2D.Double(x, y);
	}
	
    private double doSurfingMelee() {
//		if (enemyDistance < 250) 
//			FAR_HALF_PI = 1.25;
//		else
//			FAR_HALF_PI = Math.PI / 2.0;
        EnemyWave surfWave = getClosestSurfableWave();
		
		if (surfWave != null && surfWave != lastSurfWave)
			surfWave.firstLocation = (Point2D.Double) _myLocation.clone();

		ramming = false;
		/*
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
		} */
	
        if (surfWave == null) {	
			setMaxVelocity(8);
			if (_myLocation != null && eInfo != null && eInfo.location != null) {
				if (eInfo.sre.getEnergy() <= 0.0) { // ram the disabled enemy
					setBackAsFront(this, absoluteBearing(_myLocation, eInfo.location));
					ramming = true;
					return 0;
				}
				//setTurnRightRadians(Utils.normalRelativeAngle(FAR_HALF_PI + absoluteBearing(_myLocation, _enemyLocation) - getHeadingRadians()));
			}
			goTo(nearestCorner(_myLocation));
			return 0;
		}
	
		double bulletTravelTime = _myLocation.distance(surfWave.fireLocation) / surfWave.bulletVelocity;
		if (Math.random() < 0.05 * bulletTravelTime / 25) randomDirection = -randomDirection;
		
//        double goAngle = absoluteBearing(surfWave.fireLocation, _myLocation); // orbit fire location
//        double goAngle = absoluteBearing(surfWave.fireLocation, surfWave.firstLocation); // straight
//		double goAngle = absoluteBearing(_enemyLocation, _myLocation); // orbit enemy
		
		double v = 8, velocity = 8;
		double minDanger = Double.POSITIVE_INFINITY;
		double straightAngle = absoluteBearing(surfWave.fireLocation, surfWave.firstLocation); // straight
		double angle;
		double goAngle = straightAngle - Math.PI / 2;
		double cDanger;
		EnemyWave surfWave2 = getClosestSurfableWave2(surfWave);
		
		Point2D.Double p, bestPosition = nearestCorner(_myLocation);
		angle = straightAngle - Math.PI / 2;
		p = predictPositionAngle(surfWave, angle, v);
		cDanger = checkDanger(surfWave, p);
		//cDanger = checkDangerAngleAll(surfWave, angle, v);
		if (cDanger < minDanger) {
			minDanger = cDanger;
			goAngle = angle;
			velocity = v;
			bestPosition = p;
		}
		angle = straightAngle + Math.PI / 2;
		p = predictPositionAngle(surfWave, angle, v);
		cDanger = checkDanger(surfWave, p);
		//cDanger = checkDangerAngleAll(surfWave, angle, v);
		if (cDanger < minDanger) {
			minDanger = cDanger;
			goAngle = angle;
			velocity = v;
			bestPosition = p;
		}
		final double step = Math.PI / 16;
		for (angle = straightAngle - Math.PI / 2 + step; angle < straightAngle - Math.PI / 2 + Math.PI * 2; angle += step) {
			//for (v = 8; v >= 0; v -= 8) {
				p = predictPositionAngle(surfWave, angle, v);
				cDanger = checkDanger(surfWave, p);
				//cDanger = checkDangerAngleAll(surfWave, angle, v);
				if (cDanger < minDanger) {
					minDanger = cDanger;
					goAngle = angle;
					velocity = v;
					bestPosition = p;
				}
			//}
		}	

		//System.out.println(velocity);
		//goAngle = wallSmoothing(_myLocation, goAngle); 
		//setBackAsFront(this, goAngle);
		//setMaxVelocity(velocity);
		goTo(bestPosition);
		lastSurfWave = surfWave;
		//lastSurfDir = surfDir;
		return goAngle;
    }

    // This can be defined as an inner class if you want.
    static class EnemyWave {
        Point2D.Double fireLocation;
		Point2D.Double firstLocation;
        long fireTime;
        double bulletVelocity, directAngle, distanceTraveled;
        int direction;
		private static double MAX_DISTANCE = Math.hypot(1000, 1000);
		double[] info;
		static Vector[] factorLogs = new Vector[ROBOT_INDEXES];
		Vector factorLog;
		static Vector factorLogFlat = new Vector(10000); // flat movement
		double maxEscapeAngle;
		double[] buffer;
			
        public EnemyWave(int robotIndex, double enemyBulletPower, double[] _info, Point2D.Double _fireLocation) {
            bulletVelocity = bulletVelocity(enemyBulletPower);
			maxEscapeAngle = maxEscapeAngle(bulletVelocity);
			info = _info;
			fireLocation = _fireLocation;
			factorLog = factorLogs[robotIndex];
			
			updateBuffer(); // make buffer;
		} // ctor
	
		public void updateBuffer() {
			Vector log = factorLog;
			if (flattening) log = factorLogFlat;
			final int NUM_NEAREST = Math.min(NUM_SAMPLES, log.size());
			FactorLog[] nearestLogs = new FactorLog[NUM_NEAREST];
			double[] diffs = new double[NUM_NEAREST];
			int maxIndex = 0;
			for (int i = 0; i < NUM_NEAREST; i++) {
				diffs[i] = Double.POSITIVE_INFINITY;
			}
		
			// fill nearestLogs
			for (int i = log.size() - 1; i >=0; i--) {
				FactorLog fl = (FactorLog) log.get(i);
				double d = difference(info, fl.info);
				if (d < diffs[maxIndex]) {
					diffs[maxIndex] = d;
					nearestLogs[maxIndex] = fl;
					for (int j = 0; j < NUM_NEAREST; j++) {
						if (diffs[j] > diffs[maxIndex]) maxIndex = j;
					} 	
				}
			}
			
			// make buffer
			// double angleThreshold = Math.atan(18.0 / fireLocation.distance(_myLocation)); // 18.0
			double angleThreshold = Math.atan(1.0 / fireLocation.distance(_myLocation)); // pixel accuracy
			double botHalfWidthRadians = Math.atan((51.0  / 2.0) / fireLocation.distance(_myLocation));
			
			final int binSize = (int) (maxEscapeAngle * 2.0 / angleThreshold) + 1;
			final int binBotHalfWidth = (int) (maxEscapeAngle * 2.0 / botHalfWidthRadians) + 1;
			buffer = new double[binSize];
			for (int i = 0; i < NUM_NEAREST; i++) {
				if (nearestLogs[i] != null) {
					double factor = nearestLogs[i].factor;
					int index = (int) (normalize(-1.0, factor, 1.0) * (binSize - 1));
					/*
	      		    for (int x = 0; x < binSize; x++) { // bin smoothing
						double increment = nearestLogs[i].weight / (square(index - x) + 1);
      			      	buffer[x] += increment;
					}
					for (int x = Math.max(0, index - binBotHalfWidth); x < Math.min(binSize - 1, index + binBotHalfWidth); x++) {
						buffer[x] += 1;
					}*/
					int xLow = Math.max(0, index - binBotHalfWidth);
					int xHigh = Math.min(binSize - 1, index + binBotHalfWidth);
	      		    for (int x = 0; x < binSize; x++) { // bin smoothing
						double increment = nearestLogs[i].weight / diffs[i];
						if (x < xLow || x > xHigh) 
							increment = nearestLogs[i].weight / (square(index - x) + 1) / diffs[i];
      			      	buffer[x] += increment;
					}							
				}
			}						
		}       
    }

	private static final double WALL_STICK = 160; // 160
    public double wallSmoothing(Point2D.Double botLocation, double angle) {
		double angle2 = angle;
        while (true) {
			if (_moveFieldRect.contains(project(botLocation, angle, WALL_STICK))) { // 160
				return angle;
			} else if (_moveFieldRect.contains(project(botLocation, angle2, WALL_STICK))) {
				return angle2;
			}
            angle += 0.05;
			angle2 -= 0.05;
        }
    }

    public double wallSmoothing(Point2D.Double botLocation, double angle, int orientation) {
		double increment = orientation * 0.05;
        while (!_moveFieldRect.contains(project(botLocation, angle, WALL_STICK))) {
            angle += increment;
        }
        return angle;
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

/*
    public static void setBackAsFront(AdvancedRobot robot, double angle) {
        angle =
            Utils.normalRelativeAngle(angle - robot.getHeadingRadians());		
		double turnAngle = Math.atan(Math.tan(angle));
	    robot.setTurnRightRadians(turnAngle);
        robot.setAhead(angle == turnAngle ? 50 : -50);

		robot.setMaxVelocity(Rules.MAX_VELOCITY);
	}
*/

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
		double distance = _myLocation.distance(destination);
		
		// setTurnRightRadians(turnAngle);
		//this is hacked so that the bot doesn't turn once we get to our destination
		setTurnRightRadians(turnAngle * Math.signum(Math.abs((int)distance)));
		
		setAhead(distance * (angle == turnAngle ? 1 : -1));
		
		// Hit the brake pedal hard if we need to turn sharply
		// setMaxVelocity(Math.abs(getTurnRemaining()) > 30 ? 1.0 : Rules.MAX_VELOCITY);
		
		if (getOthers() > 1 && wallDistance(_myLocation) < 40.0) {
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

	private static double cornerDistance(Point2D location) {
		double x, y;
		x = location.getX();
		y = location.getY();
		if (x > field.getWidth() / 2.0) x = field.getWidth() - x;
		if (y > field.getHeight() / 2.0) y = field.getHeight() - y;
		double d = Math.hypot(x, y);
		
		return d;
	}

	private static double square(double x) {
		return x * x;
	}

	private static double distanceSquare(Point2D.Double src, Point2D.Double dest) {
		return square(src.x - dest.x) + square(src.y - dest.y);
	}

	private static double normalize(double min, double x, double max) {
		double width = max - min;
		return (limit(min, x, max) + width / 2.0) / width;
	}

	static class FactorLog {
		double[] info;
		double factor;
		double weight;
		FactorLog(double[] _info, double _factor, double _weight) {
			info = _info;
			factor = _factor;
			weight = _weight;
		}
	}

	private static double difference(double[] x1, double[] x2) {
		final int SIZE = x1.length;
		double r = 0.0;
		for (int i = 0; i < SIZE; i++) {
			double d = x1[i] - x2[i];
			r += d * d;
		}
		return r;
	}

	static class FireTimeLog {
		double[] info;
		int fireTime;
		double weight;
		FireTimeLog(double[] _info, int _fireTime, double _weight) {
			info = _info;
			fireTime = _fireTime;
			weight = _weight;
		}
	}
}
