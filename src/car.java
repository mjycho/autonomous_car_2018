

import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Area;
import java.awt.geom.AffineTransform;

public class car {

	// Common for all cars
	static final int carWidth=24;
	static final float gamma1 = 0.5f;
	static final float gamma2 = 0.5f;
	static final int[] lineAngleValues={ 45,68,90,113,135 };

	static final int carLength=50;
	static final int carRadius=carLength;

	float x=0;		// X center of car
	float y=0;		// Y center of car

	float prevX = 0;
	float prevY = 0;

	float speedCar=0;
	float angleCar=0;

	boolean collision=false;

	float reward=0.0f;

	float[] stati=new float[7];

	int[] lineX=new int[c.numOfDetectLine];
	int[] lineY=new int[c.numOfDetectLine];

	int[][] lidarCircleX= new int[5][5];
	int[][] lidarCircleY= new int[5][5];

	float storedBaseAngle = 0.0f;

	public void resetCarLoc(int startX, int startY, float startSpd, float startAngleCar ) {
		collision = false;
		x=(float)startX;
		prevX = (float)startX;
		y=(float)startY;
		prevY = (float)startY;
		speedCar=startSpd;
		angleCar=startAngleCar;
	}

	public boolean wasCollision() {
		return collision;
	}

	public float[] getStatus(float baseAngle){
		stati[0]=speedCar/c.maxSpeed;
		float temp = angleCar - baseAngle;
		if(temp<=-180) temp = 360+ temp;
		else if(temp>=180) temp = temp - 360;
		//	System.out.println("\nRelativeAngle= "+temp);
		stati[1]= (temp)/90.0f;
		storedBaseAngle = baseAngle;

		return stati;
	}

	public void turnRight(float angle) {
		angleCar = angleCar + angle;
		if(angleCar>180) angleCar -= 360;
	}

	public void turnLeft(float angle) {
		angleCar = angleCar - angle;
		if(angleCar<-180) angleCar += 360;
	}

	public  double getAngle(){
		return angleCar;
	}

	public float getReward() {
		return reward;
	}
	public float getSpeed() {
		return speedCar;
	}

	public void acceleration(float spd_inc) {
		speedCar=speedCar+spd_inc;
		if(speedCar>c.maxSpeed) 	speedCar=c.maxSpeed;
	}

	public void deceleration(float spd_dec) {
		speedCar=speedCar-spd_dec;
		if(speedCar<c.minSpeed) 	speedCar=c.minSpeed;
	}

	public void prepareCar(int currCar, int key) {
		for(int j=0;j<c.numOfDetectLine;j++){
			prepareDetectLine(angleCar, x, y, j, currCar, key);
		}
	}

	public void prepareDetectLine(float carAngle, float x, float y, int sensorNumber, int currCar, int key) {
		float offset=30.0f;

		int lidarX=(int) (x-(c.LIDARLength+10)*Math.cos(Math.toRadians(lineAngleValues[sensorNumber]+carAngle-270)));
		int lidarY=(int) (y-(c.LIDARLength+10)*Math.sin(Math.toRadians(lineAngleValues[sensorNumber]+carAngle-270)));

		//double slope=(carCenterY-Y)/(carCenterX-X);
		int numOfPoints=5;
		float yDiff=(lidarY-y)/(float)numOfPoints;
		float xDiff=(lidarX-x)/(float)numOfPoints;
		float circCount=0;

		lineX[sensorNumber] = lidarX;
		lineY[sensorNumber] = lidarY;

		for(int i=0;i<=numOfPoints-1;i++){
			if(i==0){
				if(sensorNumber==0 || sensorNumber==4){
					offset=16.0f;
				}
				else{
					offset=22.0f;

				}
			}
			else{
				if(sensorNumber==0 || sensorNumber==4){
					offset=31.0f;
				}else{
					offset=30.0f;
				}
			}
			int currX=(int)(x+xDiff*i-offset*Math.cos(Math.toRadians(lineAngleValues[sensorNumber]+carAngle-270)));
			int currY=(int)(y+yDiff*i-offset*Math.sin(Math.toRadians(lineAngleValues[sensorNumber]+carAngle-270)));

			lidarCircleX[sensorNumber][i]=currX;
			lidarCircleY[sensorNumber][i]=currY;

			if((currX<c.outerTopLeftX) || (currX>c.outerBotRightX) || (currY>c.outerBotRightY) || (currY<c.outerTopLeftY) ){
				break;
			}
			else if((currX>c.innerTopLeftX && currX<c.innerBotRightX) && (currY>c.innerTopLeftY && currY<c.innerBotRightY && key==0)){
				break;
			}
			else if(key==1){
				if((currX>c.WALL_X1 && currX<c.WALL_X2)        && (currY>c.WALL_Y1 && currY<c.WALL_Y2)){//top left
					break;
				} else if((currX>c.WALL_X3 && currX<c.WALL_X4) && (currY>c.WALL_Y1 && currY<c.WALL_Y2)){//top right
					break;
				} else if((currX>c.WALL_X1 && currX<c.WALL_X2) && (currY>c.WALL_Y3 && currY<c.WALL_Y4)){//bot left
					break;
				} else if((currX>c.WALL_X3 && currX<c.WALL_X4) && (currY>c.WALL_Y3 && currY<c.WALL_Y4)){//bot right
					break;
				}
			}

			if(c.car2car_enable && detectCarToCar(currX, currY, currCar)) {
				break;
			} 
			if(c.car2block_enable && detectCarToBlock(currX, currY)){
				break;
			}
			circCount++;
		}  // for
		// Store sensor status
		stati[sensorNumber+2] = (float)(1.25-circCount*0.25);

	}

	public boolean detectCarToBlock(int currX, int currY) {

		for(int i=0;i<=g.blockIdx-1;i++){
			if((currX-3>g.blockX[i] && currX+3<g.blockX[i]+c.blockSize) && 
					(currY-3>g.blockY[i] && currY+3<g.blockY[i]+c.blockSize)){
			//	System.out.println(currX+" "+currY+" "+g.blockX.get(i)+" "+g.blockY.get(i));
				return true;
			}
		}
		return false;
	}

	public boolean detectCarToCar(int currX, int currY, int currCar) {
		//lineX
		//lineY
		for(int i=0;i<c.numOfCars;i++){
			if(i==currCar){
				continue;
			}
			float xDiff=Math.abs(currX-g.carLocationX[i]);
			float yDiff=Math.abs(currY-g.carLocationY[i]);
			float distDiff= (float) Math.sqrt( (float) Math.pow(yDiff, 2)+ (float)Math.pow(xDiff, 2));

			float angleCarPoint=(float) Math.toDegrees(Math.atan(yDiff/xDiff));

			float angleCarAxis=g.carAngles[i];

			if(Math.abs(g.carAngles[currCar])>90){
				angleCarAxis=180-Math.abs(g.carAngles[currCar]);
			} 
			angleCarAxis = Math.abs(angleCarAxis);

			float finalAngle=180-angleCarAxis-angleCarPoint;

			if (finalAngle>90){
				finalAngle=180-finalAngle;
			}

			//System.out.println(finalAngle+" "+currX+" "+currY+" "+angleCarPoint+" "+g.carAngles[currCar]+" "+angleCarAxis+" "+g.carLocationX[currCar]+" "+g.carLocationY[currCar]);
			//System.out.println(finalAngle);

			float checkDist=(-13.0f/90.0f)*finalAngle+25.0f;
			//	System.out.println(checkDist+"  "+distDiff);
			if(checkDist>distDiff+3){
				return true;
			}
		}
		return false;
	}

	public void drawCar(Graphics2D g2d, int carNum){
		g2d.setColor(Color.MAGENTA);

		// Set up for LIDAR lines
		Graphics2D g2d2 = (Graphics2D) g2d.create();
		if(g.drawLIDAR) {
			Stroke dashed = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);
			((Graphics2D) g2d2).setStroke(dashed);
		}
		for(int j=0;j<c.numOfDetectLine;j++){
			drawDetectLine(angleCar, x, y,g2d, g2d2, j);
		}
		g2d2.dispose();

		AffineTransform old = g2d.getTransform();

		g2d.rotate(Math.toRadians(getAngle()), (x-25.0f)+carLength/2, (y-12.0f)+carWidth/2);
		if(carNum==0){
			g2d.setColor(Color.RED);
		}
		else{
			g2d.setColor(Color.WHITE);
		}

		g2d.drawRect((int)(x-25.0), (int)(y-12.0), carLength, carWidth);

		g2d.drawRect( (int) ((x-25.0f)+carLength*0.9), (int)(y-12.0f), carLength/10, carWidth);
		g2d.setTransform(old);
		if(c.showBaseAngle) g2d.drawString(carNum+"         "+storedBaseAngle,(int) ((x-25.0f)+carLength/2.5), (int)((y-12.0f)+carWidth/2));
		else g2d.drawString(carNum+"", (int) ((x-25.0f)+carLength/2.5), (int)((y-12.0f)+carWidth/2) +5);
	}

	public void drawDetectLine(float carAngle, float x, float y, Graphics g2d, Graphics2D g2d2, int sensorNumber) {
		if(g.drawLIDARValue){
			if(stati[sensorNumber+2]==1.25) g2d.drawString("C", lineX[sensorNumber],lineY[sensorNumber]);
			else g2d.drawString(stati[sensorNumber+2]+"", lineX[sensorNumber],lineY[sensorNumber]);
		}
		if(g.drawLIDAR){
			g2d2.drawLine((int)x, (int)y,lineX[sensorNumber],lineY[sensorNumber]);
		}

		if(g.drawLIDARCircles){
			for(int i=0;i<5;i++){
				for(int j=0;j<5;j++){
					g2d.drawOval(lidarCircleX[i][j], lidarCircleY[i][j], 4, 4);

				}
			}
		}
	}

	public void move(){
		float ySpd= speedCar*(float)Math.sin(Math.toRadians(angleCar));
		float xSpd= speedCar*(float)Math.cos(Math.toRadians(angleCar));

		prevX = x;
		prevY = y;

		x=x+xSpd;
		y=y+ySpd;
	}
}
