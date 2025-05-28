package stelo;
import robocode.*;
import robocode.util.*;
import java.awt.Color;

public class SoRobotNanoMelee extends Robot
{
//	private static boolean corner;
//	private static double spot;

	public void run() {
		setColors(Color.BLACK, null, null);
		double x = 0, y = 120;
		double x2 = 120, y2 = 0;
	//		double[] x = {0, 100, 0, 50, 0, 150};
//		double[] y = {100, 0, 150, 0, 50, 0};
		int i;
//		corner = false;
		
		if (getX() > 500) {
			x = 1000 - x;
			x2 = 1000 - x2;
//			for (i = 0; i < 6; i++)
//				x[i] = 1000 - x[i];
		}
		if (getY() > 500) {
			y = 1000 - y;
			y2 = 1000 - y2;
//			for (i = 0; i < 6; i++)
//				y[i] = 1000 - y[i];
		}
//		goTo(x, y);
//		corner = true;
		
		while(true) {
			// Replace the next 4 lines with any behavior you would like
//			ahead(100);
			goTo(x, y);
			turnGunLeft(360);
			goTo(x2, y2);
			turnGunRight(360);
//			back(100);
		}
	}

	/**
	 * onScannedRobot: What to do when you see another robot
	 */
	public void onScannedRobot(ScannedRobotEvent e) {
//		double absBearing = Math.toRadians(e.getBearing() + getHeading());
//		turnGunRight(normalRelativeAngle(e.getBearing() + getHeading() - getGunHeading()));
//		turnGunRight(Utils.normalRelativeAngle(absBearing - Math.toRadians(getGunHeading()) + (Math.floor(Math.random() * 3.0) / 2.0) * e.getVelocity() / 11.0 * Math.sin(Math.toRadians(e.getHeading()) - absBearing)));
//		if (e.getDistance() < 500)
		double bulletPower = Math.max(0.1, Math.min(3.0, e.getEnergy() / 5.0));
		if (getEnergy() > bulletPower)
			fire(bulletPower);
//			fire(e.getEnergy() / 5);

//		fire(Math.random() * 3);
		turnGunRight(0);
//		fire(3);
//		out.println(getVelocity());
//		if (getVelocity() == 0)
//			turnGunLeft(20); // 20
			
//		if (getGunHeat() == 0)
//			turnGunLeft(1);
	}

	private void goTo(double destinationX, double destinationY) {
        double angle = Utils.normalRelativeAngle(Math.atan2(destinationX - getX(), destinationY - getY()) - Math.toRadians(getHeading()) );
		double turnAngle = Math.atan(Math.tan(angle));
        turnRight(Math.toDegrees(turnAngle));
	
        ahead(Math.sqrt((getX() - destinationX) * (getX() - destinationX) + (getY() - destinationY) * (getY() - destinationY)) * (angle == turnAngle ? 1 : -1));
        //ahead((angle == turnAngle ? 100 : -100));		
	}

	/**
	 * onHitByBullet: What to do when you're hit by a bullet
	 */
//	public void onHitByBullet(HitByBulletEvent e) {
//		if (corner) {
//			if (getVelocity() > 0)
//				turnLeft(normalRelativeAngle(90 - e.getBearing()));
//		}
//		spot = e.getHeading();
//		turnGunRight(normalRelativeAngle(e.getHeading() + 180 - getGunHeading()));
//		fire(3);
//		scan();
//	}

//	private static double normalRelativeAngle(double a) {
//		return Math.toDegrees(Utils.normalRelativeAngle(Math.toRadians(a)));
//	}

//	public void onHitByBullet(HitByBulletEvent e) {
//		turnGunLeft(normalRelativeAngle(e.getBearing() - getGunHeading()));
//	}
	
}
