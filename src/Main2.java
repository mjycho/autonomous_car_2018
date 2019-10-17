
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.net.CacheResponse;

import javax.swing.*;

public class Main2 extends JPanel implements ActionListener, MouseListener {

	public static car [] cars = new car[c.numOfCars];

	// Timer time;
	Graphics2D g2d;
	final int roadWidth =24*10;
	static float maxNum;
	static int maxNode;
	static float maxQAD;
	static int maxNodeAD;
	static float maxQRL;
	static int maxNodeRL;
	static int iteration=0;
	static Neural neural;
	static int achieved_cnt = 0;
	static int [] carType = new int[c.numOfCars];

	public static void main(String[] args) {

		// Create Cars
		for(int i=0;i<c.numOfCars;i++){			
			cars[i] = new car();
			//	if(i==0) 	initCarType(i, 0);
			//	else 		initCarType(i, 1);
			initCarType(i, i%11);
		}

		// Init NN
		final int numInputLayer = 7;
		final int numHiddenLayer = c.numOfHidden;
		final int numOutputLayer = 6;

		float[] currentStatus  = new float[numInputLayer];
		float[] nnTarget  = new float[numOutputLayer];
		float[] nnOutput  = new float[numOutputLayer];

		neural=new Neural(numInputLayer, numHiddenLayer, numOutputLayer);

		if(c.readWeight){
			neural.readWeights();
			g.no_random = true;
			g.trained = true;
			g.stopLearn = true;
		}
		else{
			neural.initWeight();
		}

		// Set up display
		System.out.println("SMART CAR 14 - Main2");
		JFrame frame = new JFrame("SMART CAR 13");
		frame.setVisible(true);
		frame.setSize(1920, 1000);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.add(new Main2());

		// Reinforcement Learning Loop
		int cnt=0;
		while(true) {
			for(int i=0;i<c.numOfCars;i++){

				// Get current Car Status
				currentStatus = cars[i].getStatus(getBaseAngle(i));
				cars[i].storedBaseAngle = getBaseAngle(i);
				//currentStatus = input[iteration%12];

				// Run NN
				//System.out.print("\n\ncurrent : "+i+" spd="+currentStatus[0]+" angle="+currentStatus[1]+" ");
				//for(int j=2;j<numInputLayer;j++) System.out.printf("  %f", currentStatus[j]);

				if(c.debug) {
					System.out.print("\n\nI ");
					for(int j=0;j<numInputLayer;j++) System.out.printf(" %.6f", currentStatus[j]);
				}

				nnOutput = Neural.feedforward(currentStatus);

				if(c.debug) {
					System.out.print("\nO ");
					for(int j=0;j<numOutputLayer;j++)  System.out.printf(" %.6f", nnOutput[j]);
				}

				// based on decision
				decisionMaking(nnOutput, i);

				// move the car 1 step if speed is not zero.
				cars[i].move();	

				// Pre-calculate new car location
				cars[i].prepareCar(i, 1);

				// Saves each car location for car2car conflict check
				g.carLocationX[i]=cars[i].x;
				g.carLocationY[i]=cars[i].y;
				g.carAngles[i]=(float) cars[i].getAngle();

				// Calculate reward
				for(int j=0;j<numOutputLayer;j++) nnTarget[j] = nnOutput[j]*1.0f;
				nnTarget = calcReward(nnTarget, maxQAD, maxNodeAD, maxQRL, maxNodeRL, i);

				// dynamic learnRate 
				if(g.trained) {
					neural.learnRate = c.learnRateMid;
					c.car2car_enable = true;
				} else if(cars[i].stati[2] + cars[i].stati[3] + cars[i].stati[4] + cars[i].stati[5] +cars[i].stati[6] > 0.0f ) {
					neural.learnRate = c.learnRateBig;			// *(iteration/epsilon);
				}
				else neural.learnRate = c.learnRateSmall;

				if(iteration == c.max_iteration) 		g.stopLearn = true;
				if(isTrained(i) && i==0) {
					if(achieved_cnt%2 ==0) achieved_cnt++;
					if(achieved_cnt>=20) g.trained = true;
				} else if(!isTrained(i) && i==0) {
					achieved_cnt++;
				}

				// Training
				if(i==0 && !g.stopLearn) {
					if(iteration<c.max_iteration) iteration++;
					Neural.backprop(nnTarget);
					if(c.debug) {
						System.out.print("\nT ");
						for(int j=0;j<numOutputLayer;j++) System.out.printf(" %.6f", nnTarget[j]);
					}
				} 

				// Conflict handling
				if(cars[i].wasCollision() ) {// || cars[i].x >16000) {

					initCarType(i, i%11);

					if(c.debug) {
						System.out.println("\n=============================================================================================");
					}
					cars[i].stati[2]=0.0f;
					cars[i].stati[3]=0.0f;
					cars[i].stati[4]=0.0f;
					cars[i].stati[5]=0.0f;
					cars[i].stati[6]=0.0f;
				}

				// display delay
				try {  Thread.sleep((g.trained)?g.afterTrainDelay:g.duringTrainDelay); } 
			//	try {  Thread.sleep(0); } 

				catch(InterruptedException ex) { Thread.currentThread().interrupt(); }

				//if(cnt++==100000) System.exit(1);
			}

			// repaint
			frame.repaint();

		}	// while
	}	// end of Main

	public static boolean isTrained(int carNum) {
		return true;
	}

	public static void initCarType(int carNum, int type) {
		int initX = 0;
		int initY = 0;

		switch (c.carInitSec[type]) {
		case 1 :
			initX = (c.WALL_X1 + c.WALL_X2)/2;
			initY = (c.WALL_Y0 + c.WALL_Y1)/2;
			break;
		case 2 :
			initX = (c.WALL_X2 + c.WALL_X3)/2;
			initY = (c.WALL_Y0 + c.WALL_Y1)/2;
			break;
		case 3 :
			initX = (c.WALL_X3 + c.WALL_X4)/2;
			initY = (c.WALL_Y0 + c.WALL_Y1)/2;
			break;
		case 5 :
			initX = (c.WALL_X0 + c.WALL_X1)/2;
			initY = (c.WALL_Y1 + c.WALL_Y2)/2;
			break;
		case 9 :
			initX = (c.WALL_X4 + c.WALL_X5)/2;
			initY = (c.WALL_Y1 + c.WALL_Y2)/2;
			break;
		case 11 :
			initX = (c.WALL_X1 + c.WALL_X2)/2;
			initY = (c.WALL_Y2 + c.WALL_Y3)/2;
			break;
		case 13 :
			initX = (c.WALL_X3 + c.WALL_X4)/2;
			initY = (c.WALL_Y2 + c.WALL_Y3)/2;
			break;		
		case 15 :
			initX = (c.WALL_X0 + c.WALL_X1)/2;
			initY = (c.WALL_Y3 + c.WALL_Y4)/2;
			break;
		case 19 :
			initX = (c.WALL_X4 + c.WALL_X5)/2;
			initY = (c.WALL_Y3 + c.WALL_Y4)/2;
			break;
		case 21 :
			initX = (c.WALL_X1 + c.WALL_X2)/2;
			initY = (c.WALL_Y4 + c.WALL_Y5)/2;
			break;
		case 23 :
			initX = (c.WALL_X3 + c.WALL_X4)/2;
			initY = (c.WALL_Y4 + c.WALL_Y5)/2;
			break;	
		default  :
			System.out.println("ERROR : Invalid Section");
			System.exit(1);
		}

		cars[carNum].resetCarLoc(initX, initY, 1.0f, c.carInitAngle[type] );

		//	cars[carNum].resetCarLoc(initX, initY, c.carInitSpd[type], c.carInitAngle[type] );
		carType[carNum] = type;
	}

	private static int maxOf(float [] numbers) {
		float maxNum = numbers[0];
		int maxNode = 0;
		for(int i=1; i<numbers.length;i++) {
			if(numbers[i]>maxNum) {
				maxNum=numbers[i];
				maxNode=i;
			}
		}
		return(maxNode);
	}

	public static int giveRand(int i, int j) {
		return i + (int)(Math.random() * (j-i));  
	}

	private static float giveRandFloat(int i, int j) {
		return (float) (i + (Math.random() * (j-i)));  
	}


	public void paint(Graphics g) {
		super.paint(g);
		g2d = (Graphics2D) g;

		// Draw Road
		drawRoad();

		// Draw Info
		drawInfo();

		// Draw Cars
		for(int i=0;i<c.numOfCars;i++){
			cars[i].drawCar(g2d, i);
		}
	}

	private void drawInfo() {
		g2d.setColor(Color.BLACK);
		g2d.setFont(new Font("TimesRoman", Font.PLAIN, 20)); 
		g2d.drawString("CAR NUMERO UNO", 900, 480);
		g2d.drawString("iteration: "+iteration, 900, 510);
		g2d.drawString("CarSpeed: "+String.format("%.4f",cars[0].getSpeed()), 900, 540);
		g2d.drawString("CarAngle: "+String.format("%.4f",cars[0].getAngle()), 900, 570);
		g2d.drawString("Reward: "+String.format("%.4f",cars[0].getReward()), 900, 600);
		g2d.drawString("Learn rate : "+String.format("%.4f", neural.learnRate), 900, 630);
		g2d.setFont(new Font("TimesRoman", Font.PLAIN, 15)); 
	}

	private void drawRoad() {
		g2d.setColor(Color.WHITE);
		g2d.fillRect(0,0,2000,1100);		// Fill base with WHITE
		g2d.setColor(Color.BLACK);
		g2d.fillRect(c.WALL_X0, c.WALL_Y0, c.WALL_X5-c.WALL_X0, c.WALL_Y5-c.WALL_Y0);
		g2d.setColor(Color.GREEN);
		g2d.fillRect(c.WALL_X1, c.WALL_Y1, c.WALL_X2-c.WALL_X1, c.WALL_Y2-c.WALL_Y1);
		g2d.fillRect(c.WALL_X3, c.WALL_Y1, c.WALL_X4-c.WALL_X3, c.WALL_Y2-c.WALL_Y1);
		g2d.fillRect(c.WALL_X1, c.WALL_Y3, c.WALL_X2-c.WALL_X1, c.WALL_Y4-c.WALL_Y3);
		g2d.fillRect(c.WALL_X3, c.WALL_Y3, c.WALL_X4-c.WALL_X3, c.WALL_Y4-c.WALL_Y3);
	}

	public static float getBaseAngle(int carNum) {
		float baseAngle=0;

		return(c.carBaseAngle[carType[carNum]][getSection(carNum)]);
	}

	private static int getSection (int carNum) {
		int row;
		int col;

		if(cars[carNum].y<c.WALL_Y1) 		row = 0;
		else if(cars[carNum].y<c.WALL_Y2)	row = 1;
		else if(cars[carNum].y<c.WALL_Y3)	row = 2;
		else if(cars[carNum].y<c.WALL_Y4)	row = 3;
		else                            	row = 4;

		if(cars[carNum].x<c.WALL_X1) 		col = 0;
		else if(cars[carNum].x<c.WALL_X2)	col = 1;
		else if(cars[carNum].x<c.WALL_X3)	col = 2;
		else if(cars[carNum].x<c.WALL_X4)	col = 3;
		else                            	col = 4;

		return(row*5 + col);
	}

	public static void decisionMaking(float [] nnOutput, int carNum) {
		int max_output ;
		int max_outputAD;
		int max_outputRL;

		if(!g.no_random && (int)(Math.random() * c.max_iteration) > iteration) { //iteration) {	// random
			max_outputAD = giveRand(0,3);
			max_outputRL = giveRand(3,6);
			if(c.debug) System.out.print(" rd ");
		} else {	// NN
			if(nnOutput[0]>nnOutput[1]) {
				if(nnOutput[2]>nnOutput[0]) max_outputAD = 2;
				else max_outputAD = 0;
			} else {
				if(nnOutput[2]>nnOutput[1]) max_outputAD = 2;
				else max_outputAD = 1;
			}

			if(nnOutput[3]>nnOutput[4]) {
				if(nnOutput[5] > nnOutput[3]) max_outputRL =5;
				else max_outputRL = 3;
			} else {
				if(nnOutput[5] > nnOutput[4]) max_outputRL =5;
				else max_outputRL = 4;
			}

			if(c.debug) System.out.print(" nn");
		}

		//max_outputAD = 2;	// for now, no speed change, only right or left

		if(max_outputAD ==0) {
			cars[carNum].acceleration(0.1f);
			maxQAD = nnOutput[0];
			maxNodeAD = 0;
			//	System.out.print("  acc");
		} else if(max_outputAD ==1 ){
			cars[carNum].deceleration(0.1f);
			maxQAD = nnOutput[1];
			maxNodeAD = 1;
			//	System.out.print("  dec");
		} else {
			maxQAD = nnOutput[2];
			maxNodeAD = 2;
			//	System.out.print("  ad_none");
		}

		if(max_outputRL==3) {
			cars[carNum].turnRight(2.0f);
			maxQRL = nnOutput[3];
			maxNodeRL = 3;
			if(c.debug) System.out.print("  right");
		} else if(max_outputRL==4) {
			cars[carNum].turnLeft(2.0f);
			maxQRL = nnOutput[4];
			maxNodeRL = 4;
			if(c.debug) System.out.print("  left");
		} else {
			maxQRL = nnOutput[5];
			maxNodeRL = 5;
			if(c.debug) System.out.print("  rl_none");
		}		
	}

	public static float [] calcReward(float [] nnOutput, float maxQAD, int maxNodeAD, float maxQRL, int maxNodeRL, int carNum) {

		float reward=0.0f;
		// if(sum of sensor value is >0), small negative reward.
		// if(there is a conflict, big negative reward. Needs to return car to start point.
		for(int i=0;i<c.numOfDetectLine;i++) {
			if(cars[carNum].stati[i+2]>1.0) { 	// conflict
				//	reward = reward -20;
				cars[carNum].collision = true;
				break;
			} else reward = reward -cars[carNum].stati[i+2]*1.5f;		// something there, negative reward
		}

		// if the car advanced, positive reward.
		//		reward = reward + (float)(x-prevX)*1.0f;
		final float distanceFactor = 1.0f;
		float baseAngle = getBaseAngle(carNum);
		float xDiff = cars[carNum].x-cars[carNum].prevX;
		float yDiff = cars[carNum].y-cars[carNum].prevY;

		if(baseAngle==0) 		reward = reward + (float)(xDiff)*distanceFactor;
		else if(baseAngle==90) 	reward = reward + (float)(yDiff)*distanceFactor;
		else if(baseAngle==-90) 	reward = reward - (float)(yDiff)*distanceFactor;
		else if(baseAngle==180) 	reward = reward - (float)(xDiff)*distanceFactor;
		else if(baseAngle==45) 	reward = reward + (float)(xDiff)*distanceFactor*0.5f + (float)(yDiff)*distanceFactor*0.5f;
		else if(baseAngle==-45) 	reward = reward + (float)(xDiff)*distanceFactor*0.5f - (float)(yDiff)*distanceFactor*0.5f;
		else if(baseAngle==135) 	reward = reward - (float)(xDiff)*distanceFactor*0.5f + (float)(yDiff)*distanceFactor*0.5f;
		else if(baseAngle==-135) reward = reward - (float)(xDiff)*distanceFactor*0.5f - (float)(yDiff)*distanceFactor*0.5f;

		// if the car has angle to forward direction, small negative reward.
		float angleDiff = cars[carNum].angleCar-baseAngle;
		if(angleDiff<=-180) angleDiff = 360 + angleDiff;
		else if(angleDiff>=180) angleDiff = angleDiff - 360;
		reward = reward - (Math.abs(angleDiff) / 30.0f);


		if(reward<-10) reward = -10;
		if(reward>10)  reward = 10;
		reward = reward / 20.0f + 0.5f; 	// 0.0 ~ 1.0
		if(cars[carNum].collision) reward = -10.0f;

		if(c.debug) System.out.printf(" R %.6f",reward);

		nnOutput[maxNodeRL] = reward;

		return nnOutput;
	}
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//
	//Buttons
	//
	/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public void mouseClicked(MouseEvent e) {  
		g.duringTrainDelay=Math.abs(g.duringTrainDelay-1);
		g.afterTrainDelay=Math.abs(g.afterTrainDelay-1);
	}
	
	public Main2() {		
		addMouseListener(this);
	}


	public void mouseEntered(MouseEvent e) {}  
	public void mouseExited(MouseEvent e) {}  
	public void mousePressed(MouseEvent e) {}  
	public void mouseReleased(MouseEvent e) {}  
	public void actionPerformed(ActionEvent e) {}

}
