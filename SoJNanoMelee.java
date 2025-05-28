// SoJNanoMelee
// (C) 2007 Stelokim
//

package stelo;
import robocode.*;
import robocode.util.*;
import java.awt.Color;

public class SoJNanoMelee extends JuniorRobot
{
	private static boolean spot;
	private static final int WALL_DISTANCE = 120;
				
	public void run() {
		double x = 0, y = WALL_DISTANCE; // 120
		double x2 = WALL_DISTANCE, y2 = 0;
		
		if (robotX > 500) {
			x = 1000 - x;
			x2 = 1000 - x2;
		}
		if (robotY > 500) {
			y = 1000 - y;
			y2 = 1000 - y2;
		}
//		while(true) {
			goTo(x, y);
			goTo(x2, y2);
			turnGunRight(Integer.MAX_VALUE);
//		}
	}

	/**
	 * onScannedRobot: What to do when you see another robot
	 */
	public void onScannedRobot() {
		double bulletPower = Math.max(0.1, Math.min(3.0, scannedEnergy / 5.0));
		if (gunReady && energy > bulletPower) {
			turnGunTo(scannedAngle);
			fire(bulletPower);
		}
		turnGunRight(0);
	}

	private void goTo(double destinationX, double destinationY) {
        double angle = Utils.normalRelativeAngle(Math.atan2(destinationX - robotX, destinationY - robotY) - Math.toRadians(heading) );
		double turnAngle = Math.atan(Math.tan(angle));

        turnRight((int) Math.toDegrees(turnAngle));

        ahead((int) Math.sqrt((robotX - destinationX) * (robotX - destinationX) + (robotY - destinationY) * (robotY - destinationY)) * (angle == turnAngle ? 1 : -1));

//		turnAheadRight((int) Math.sqrt((robotX - destinationX) * (robotX - destinationX) + (robotY - destinationY) * (robotY - destinationY)) * (angle == turnAngle ? 1 : -1), (int) Math.toDegrees(turnAngle));
//        ahead((angle == turnAngle ? WALL_DISTANCE : -WALL_DISTANCE));		
	}

//	public void onHitWall() {
//		run();
//	}

//	private static double normalRelativeAngle(double a) {
//		return Math.toDegrees(Utils.normalRelativeAngle(Math.toRadians(a)));
//	}
}
