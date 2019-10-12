
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.CacheResponse;
import java.util.Stack;
import javax.imageio.ImageIO;
import javax.swing.*;


public class Main extends JPanel implements ActionListener, MouseListener{

	public static car [] cars = new car[c.numOfCars];

	Graphics2D g2d;
	
	static float maxNum;
	static float maxQAD;
	static int maxNodeAD;
	static float maxQRL;
	static int maxNodeRL;
	static int iteration=0;
	static int trainedIteration = 0;
	static Neural neural;
	static int achieved0_cnt = 0;
	static int achieved1_cnt = 0;
	static boolean trained0 = false;
	static boolean trained1 = false;
	static final int tdBufferDepth = 20;
	static float[] graphValueIn=new float[7];
	static float[] graphValueOut=new float[7];
	static float[][] mean = new float[c.numOfCars][7];
	static int[] inputCounter=new int[c.numOfCars];
	static float[][] numerators=new float[c.numOfCars][7];
	static float[][] stdDev=new float[c.numOfCars][7];
	static float[][] stdDevDiff=new float[c.numOfCars][7];
	static float LIDARLearnRate=0.0f;
	static float SDLearnRate=0.0f;
	static float graphValueLearnRate=0.0f;
	static private BufferedImage image;
	
	public static void main(String[] args) throws IOException {
		
		final int numInputLayer = 7;
		final int numHiddenLayer = c.numOfHidden;
		final int numOutputLayer = 6;
		float[] currentStatus  = new float[numInputLayer];
		float[] nnTarget  = new float[numOutputLayer];
		float[] nnOutput  = new float[numOutputLayer];
		float [][][] carStatusBuff = new float[c.tdBufferDepth][c.numOfCars][numInputLayer];	
		int [][]   carTargetRLBuff = new int[c.tdBufferDepth][c.numOfCars];	
		int [][]   carTargetADBuff = new int[c.tdBufferDepth][c.numOfCars];	
		int [] tdBufferPtr = new int[c.numOfCars];

		// Create Cars
		for(int i=0;i<c.numOfCars;i++){			
			cars[i] = new car();
			if(i==0) 	initCarType(i, 0);
			else 		initCarType(i, 1);
		}
		
		// Create NN
		neural=new Neural(numInputLayer, numHiddenLayer, numOutputLayer);
		if(c.readWeight) {
			neural.readWeights();
			g.no_random = true;
			g.trained = true;
			g.stopLearn = true;
		}
		else neural.initWeight();

		// Set up display
		System.out.println("SMART CAR 14");
		JFrame frame = new JFrame("SMART CAR 14");
		frame.setSize(1920, 1000);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.add(new Main());

		try {
			image = ImageIO.read(new File("neuron_figure.png"));
		} catch (IOException e) {
			System.out.println("File Error");
		}
		frame.setVisible(true);
		
		// Reinforcement Learning Loop
		while(true) {
			for(int i=0;i<c.numOfCars;i++){
				// Get current Car Status
				currentStatus = cars[i].getStatus(getBaseAngle(cars[i].x, cars[i].y, i));

				if(c.debug) {
					System.out.print("\n\nI ");
					for(int j=0;j<numInputLayer;j++) System.out.printf(" %.6f", currentStatus[j]);
				}

				if(i == 0) {
					System.out.printf("I");
					for(int j=0;j<numInputLayer;j++) System.out.printf(" %.6f", currentStatus[j]);
					System.out.printf("\n");
				}
				nnOutput = Neural.feedforward(currentStatus);
				if(i == 0) {
					System.out.printf("O");
					for(int j=0;j<numOutputLayer;j++) System.out.printf(" %.6f", nnOutput[j]);
					System.out.printf("\n");
				}
				
				if(c.tdLearn) {
					for(int j=0;j<numInputLayer;j++) {
						carStatusBuff[tdBufferPtr[i]][i][j] = currentStatus[j];
					}
				}

				if(c.debug) {
					System.out.print("\nO ");
					for(int j=0;j<numOutputLayer;j++)  System.out.printf(" %.6f", nnOutput[j]);
				}

				// based on decision
				decisionMaking(nnOutput, i);

				// move the car 1 step if speed is not zero.
				cars[i].move();	

				// Pre-calculate new car location
				cars[i].prepareCar(i, 0);

				// Saves each car location for car2car conflict check
				g.carLocationX[i]=cars[i].x;
				g.carLocationY[i]=cars[i].y;
				g.carAngles[i]=(float) cars[i].getAngle();

				// Calculate reward
				for(int j=0;j<numOutputLayer;j++) nnTarget[j] = nnOutput[j];
				nnTarget = calcReward(nnTarget, maxQAD, maxNodeAD, maxQRL, maxNodeRL, i);

				// dynamic learnRate 
				if(((i==0) && trained0) || ((i==1) && trained1)) {
					neural.learnRate = c.learnRateMid;
				} else {
					if(c.trainOnLIDAR) {  // LIDAR sensor to adjust learnRate
						if(cars[i].stati[2] + cars[i].stati[3] + cars[i].stati[4] + cars[i].stati[5] +cars[i].stati[6] > 0.0f) {
							LIDARLearnRate = c.learnRateBig;
						} else LIDARLearnRate = c.learnRateSmall;
					}

					if(c.trainOnSD) { 	// Standard Deviation to adjust learnRate
						SDLearnRate = c.learnRateBig * standardDevLearn(i, currentStatus)  * 0.5f;
						//		System.out.println("\nSDLearnRate : "+SDLearnRate);
					}

					if(c.LIDARPriority) {
						if(LIDARLearnRate == c.learnRateSmall) 	neural.learnRate = SDLearnRate;
						else 									neural.learnRate = LIDARLearnRate;
					} else if(c.LIDARSDAvg) 					neural.learnRate = (LIDARLearnRate+SDLearnRate)/2.0f;
					else if(c.LIDARSDAdd) 						neural.learnRate = LIDARLearnRate+SDLearnRate;
					else if(c.trainOnLIDAR)						neural.learnRate = LIDARLearnRate;
					else if(c.trainOnSD)						neural.learnRate = SDLearnRate;
				}

				if(crossIn(i) && i==0 && !g.trained)  {
					achieved0_cnt++;
					if(achieved0_cnt>=c.how_many_pass_for_pass) trained0 = true;
				}

				if(crossIn(i) && i==1 && !g.trained)  {
					achieved1_cnt++;
					if(achieved1_cnt>=c.how_many_pass_for_pass) trained1= true;
				}		
				if(trained0 && trained1 &&!g.trained) {	// visit only once
					g.trained= true;
					g.no_random = true;
					if(c.saveWeightOnSuccess){
						neural.writeWeights();	
					}
					System.out.println("Trained Iteration : " + iteration);
					trainedIteration = iteration;
				}
				
				if(!c.readWeight  && !g.stopLearn) {
					if(c.tdLearn) {
						carTargetRLBuff[tdBufferPtr[i]][i] = maxNodeRL;
						carTargetADBuff[tdBufferPtr[i]][i] = maxNodeAD;
						tdBufferPtr[i]++;
						if(tdBufferPtr[i]==c.tdBufferDepth) tdBufferPtr[i]=0;
					}

					if(iteration<c.max_iteration) iteration++;

					if(iteration==c.max_iteration) {
						if(c.logError) neural.closeLoggingError();
						g.stopLearn = true;
						g.afterTrainDelay=1;

						if(c.afterDone) afterDone();					
					}

					if(g.trained) neural.learnRate = 0.0f;

					Neural.backprop(nnTarget);
					
					if(c.debug) {
						System.out.print("\nT ");
						for(int j=0;j<numOutputLayer;j++) System.out.printf(" %.6f", nnTarget[j]);
					}
				} 

				//Preparing for stat graph
				if(i==0){
					for(int j=0;j<=numInputLayer-1;j++){
						graphValueIn[j]=currentStatus[j];
					}
					for(int j=0;j<=numOutputLayer-1;j++){
						graphValueOut[j]=nnOutput[j];
					}
					graphValueLearnRate=neural.learnRate;
				}

				// ER training
				if(c.tdLearn && !g.stopLearn && !g.trained) {
					if(cars[i].wasCollision() ) {
						int tdLearnCnt = c.tdLearnPatternNum;
						int tdLearnIdx = tdBufferPtr[i];
						float tdLearnRate = neural.learnRate;

						while(tdLearnCnt>0) {

							// Adjust Learn Rate
							neural.learnRate = tdLearnRate *((float)tdLearnCnt/(float)c.tdLearnPatternNum);

							if(c.td_debug) {
								System.out.print("\n\nTDn Cnt : "+tdLearnCnt + "  "+ neural.learnRate );
							}
							tdLearnIdx--;
							tdLearnCnt--;
							if(tdLearnIdx<0) tdLearnIdx = c.tdBufferDepth-1;

							if(c.td_debug) {
								System.out.print("\n\nI ");
								for(int j=0;j<numInputLayer;j++) System.out.printf(" %.6f", carStatusBuff[tdLearnIdx][i][j]);
							}

							// Feedforward
							nnOutput = Neural.feedforward(carStatusBuff[tdLearnIdx][i]);

							if(c.td_debug) {
								System.out.print("\nO ");
								for(int j=0;j<numOutputLayer;j++)  System.out.printf(" %.6f", nnOutput[j]);
							}

							// Set up nnTarget
							for(int j=0;j<nnTarget.length;j++) {
								if(j==carTargetRLBuff[tdLearnIdx][i]) 		nnTarget[j] = nnOutput[j] * c.tdDiscount;
								else if(j==carTargetADBuff[tdLearnIdx][i]) 	nnTarget[j] = nnOutput[j] * c.tdDiscount;
								else 										nnTarget[j] = nnOutput[j];
							}

							if(c.td_debug) {
								System.out.print("\nT ");
								for(int j=0;j<numOutputLayer;j++) System.out.printf(" %.6f", nnTarget[j]);
							}

							Neural.backprop(nnTarget);
							
						} // while
					}
				}

				// Conflict handling
				if(cars[i].wasCollision() || (crossOut(i)&& (i==0)))  {

					if(i==0) 	initCarType(i, 0);
					else 		initCarType(i, 1);
					if(c.debug || c.td_debug) {
						System.out.println("\n=============================================================================================");
					}
					
				//	if(i==0) 	achieved0_cnt=0;
				//	else 		achieved1_cnt=0;
					
					cars[i].stati[2]=0.0f;
					cars[i].stati[3]=0.0f;
					cars[i].stati[4]=0.0f;
					cars[i].stati[5]=0.0f;
					cars[i].stati[6]=0.0f;
				}

				// display delay
				try {  Thread.sleep(g.trained?g.afterTrainDelay:g.duringTrainDelay); } 
				catch(InterruptedException ex) { Thread.currentThread().interrupt(); }

			} // for

			// repaint
			frame.repaint();
		}	// while
	}

	
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//		Miscellaneous
//
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	public static float getBaseAngle(float x, float y, int carNum) {
		float baseAngle=0;

		// right
		if(x>c.innerBotRightX) {
			if(y>c.innerBotRightY)  	baseAngle = -45;		// right-bottom
			else if(y<c.innerTopLeftY) 	baseAngle = -135;	// right-top
			else 						baseAngle = -90;					// right most
			// left
		}	else if(x<c.innerTopLeftX) {
			if(y>c.innerBotRightY)		baseAngle = 45;		// left-bottom
			else if(y<c.innerTopLeftY) 	baseAngle = 135; 	// left-top
			else 						baseAngle = 90;	// left-most
		} else {
			if(y<c.innerTopLeftY) 		baseAngle = 180;
			else 						baseAngle = 0;
		}

		if(carNum%2==1) {
			baseAngle = baseAngle + 180;
			if(baseAngle>180) 	baseAngle = baseAngle - 360;
		}

		//	System.out.println("BaseAngel : "+baseAngle);
		return(baseAngle);
	}

	private static void afterDone() {
		BufferedWriter bw=null;
		FileWriter fw=null;

		try{
			fw = new FileWriter("sen0_sens4_rturn.txt");
			bw = new BufferedWriter(fw);	

			float[] currentStatus  = new float[7];
			float[] nnOutput  = new float[6];

			currentStatus[0] = 1.0f;
			currentStatus[1] = 0.0f;
			currentStatus[3] = 0.0f;
			currentStatus[4] = 0.0f;
			currentStatus[5] = 0.0f;

			// First line
			bw.write(0+ " ");
			for(float j=0; j<1.04f; j=j+0.05f) 
				bw.write(j+ " ");
			bw.write("\n");

			for(float i=0; i<1.04f; i=i+0.05f) {
				currentStatus[2] = i;
				bw.write(i+ " ");
				for(float j=0; j<1.04f; j=j+0.05f) {
					currentStatus[6] = j;
					nnOutput = neural.feedforward(currentStatus);
					bw.write(nnOutput[3]+ " ");
				} // for i
				bw.write("\n");
			} // for j
			bw.close();
			fw.close();
		} 	// try
		catch(IOException e){
			e.printStackTrace();
		}

		try{
			fw = new FileWriter("sen0_sens4_lturn.txt");
			bw = new BufferedWriter(fw);	

			float[] currentStatus  = new float[7];
			float[] nnOutput  = new float[6];

			currentStatus[0] = 1.0f;
			currentStatus[1] = 0.0f;
			currentStatus[3] = 0.0f;
			currentStatus[4] = 0.0f;
			currentStatus[5] = 0.0f;

			// First line
			bw.write(0+ " ");
			for(float j=0; j<1.04f; j=j+0.05f) 
				bw.write(j+ " ");
			bw.write("\n");

			for(float i=0; i<1.04f; i=i+0.05f) {
				currentStatus[2] = i;
				bw.write(i+ " ");
				for(float j=0; j<1.04f; j=j+0.05f) {
					currentStatus[6] = j;
					nnOutput = neural.feedforward(currentStatus);
					bw.write(nnOutput[4]+ " ");
				} // for i
				bw.write("\n");
			} // for j
			bw.close();
			fw.close();
		} 	// try
		catch(IOException e){
			e.printStackTrace();
		}

		try{
			fw = new FileWriter("sen0_angle_rturn.txt");
			bw = new BufferedWriter(fw);	

			float[] currentStatus  = new float[7];
			float[] nnOutput  = new float[6];

			currentStatus[0] = 1.0f;
			currentStatus[3] = 0.0f;
			currentStatus[4] = 0.0f;
			currentStatus[5] = 0.0f;
			currentStatus[6] = 0.0f;


			// First line
			bw.write(0+ " ");
			for(float j=-1.0f; j<1.04f; j=j+0.05f) 
				bw.write(j+ " ");
			bw.write("\n");

			for(float i=0; i<1.04f; i=i+0.05f) {
				currentStatus[2] = i;
				bw.write(i+ " ");
				for(float j=-1.0f; j<1.04f; j=j+0.05f) {
					currentStatus[1] = j;
					nnOutput = neural.feedforward(currentStatus);
					bw.write(nnOutput[3]+ " ");
				} // for i
				bw.write("\n");
			} // for j
			bw.close();
			fw.close();
		} 	// try
		catch(IOException e){
			e.printStackTrace();
		}

		try{
			fw = new FileWriter("sen4_angle_lturn.txt");
			bw = new BufferedWriter(fw);	

			float[] currentStatus  = new float[7];
			float[] nnOutput  = new float[6];

			currentStatus[0] = 1.0f;
			currentStatus[2] = 0.0f;
			currentStatus[3] = 0.0f;
			currentStatus[4] = 0.0f;
			currentStatus[5] = 0.0f;
			currentStatus[6] = 0.0f;

			// First line
			bw.write(0+ " ");
			for(float j=-1.0f; j<1.04f; j=j+0.05f) 
				bw.write(j+ " ");
			bw.write("\n");

			for(float i=0; i<1.04f; i=i+0.05f) {
				currentStatus[6] = i;
				bw.write(i+ " ");
				for(float j=-1.0f; j<1.04f; j=j+0.05f) {
					currentStatus[1] = j;
					nnOutput = neural.feedforward(currentStatus);
					bw.write(nnOutput[4]+ " ");
				} // for i
				bw.write("\n");
			} // for j
			bw.close();
			fw.close();
		} 	// try
		catch(IOException e){
			e.printStackTrace();
		}
		
		try{
			fw = new FileWriter("sen0_sens4_rturn_lturn.txt");
			bw = new BufferedWriter(fw);	

			float[] currentStatus  = new float[7];
			float[] nnOutput  = new float[6];

			currentStatus[0] = 1.0f;
			currentStatus[1] = 0.0f;
			currentStatus[3] = 0.0f;
			currentStatus[4] = 0.0f;
			currentStatus[5] = 0.0f;

			// First line
			bw.write(0+ " ");
			for(float j=0; j<1.04f; j=j+0.05f) 
				bw.write(j+ " ");
			bw.write("\n");

			for(float i=0; i<1.04f; i=i+0.05f) {
				currentStatus[2] = i;
				bw.write(i+ " ");
				for(float j=0; j<1.04f; j=j+0.05f) {
					currentStatus[6] = j;
					nnOutput = neural.feedforward(currentStatus);
					bw.write(nnOutput[3]-nnOutput[4]+ " ");
				} // for i
				bw.write("\n");
			} // for j
			bw.close();
			fw.close();
		} 	// try
		catch(IOException e){
			e.printStackTrace();
		}
	}


	private static boolean crossIn(int carNum) {
		if(carNum%2==0) {
			if(
					((cars[carNum].prevX < c.innerTopLeftX ) && (cars[carNum].prevY>=c.innerTopLeftY) && (cars[carNum].prevY<=c.innerBotRightY)) &&
					((cars[carNum].x < c.innerTopLeftX ) && (cars[carNum].y>=c.innerBotRightY)) ) return(true);
			else return(false);
		} else {
			if(
					((cars[carNum].prevX < c.innerTopLeftX ) && (cars[carNum].prevY>=c.innerTopLeftY) && (cars[carNum].prevY<=c.innerBotRightY)) &&
					((cars[carNum].x < c.innerTopLeftX ) && (cars[carNum].y<c.innerTopLeftY)) ) return(true);	
			else return(false);
		}
	}

	private static boolean crossOut(int carNum) {
		if(carNum%2==0) {
			if(
					((cars[carNum].prevX > c.innerTopLeftX ) && (cars[carNum].prevX<c.innerBotRightX) && (cars[carNum].prevY>c.innerBotRightY)) &&
					((cars[carNum].x < c.innerTopLeftX ) && (cars[carNum].y>=c.innerBotRightY)) ) return(true);
			else return(false);
		} else {
			if(
					((cars[carNum].prevX > c.innerTopLeftX ) && (cars[carNum].prevX<c.innerBotRightX) && (cars[carNum].prevY<c.innerTopLeftY)) &&
					((cars[carNum].x < c.innerTopLeftX ) && (cars[carNum].y<c.innerTopLeftY)) ) return(true);
			else return(false);	
		}
	}

	public static void initCarType(int carNum, int type) {
		if(carNum%2==0) 
			cars[carNum].resetCarLoc(400+giveRand(0,50), 800+giveRand(-80,0), 0.5f, (float)giveRandFloat(-20, 20));
		else
			cars[carNum].resetCarLoc(750+giveRand(0,100), 60+giveRand(-10, 10), 0.5f, (float)giveRandFloat(-20, 20));
	}

	public static int giveRand(int i, int j) {
		return i + (int)(Math.random() * (j-i));  
	}

	private static float giveRandFloat(int i, int j) {
		return (float) (i + (Math.random() * (j-i)));  
	}

/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//		Display
//
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public void paint(Graphics g) {
		super.paint(g);
		g2d = (Graphics2D) g;

		// Draw Road
		drawRoad(c.roadWidth);

		// Draw Info
		drawInfo();

		// Draw Buttons
		drawButtons();

		// Draw Blocks
		drawBlocks();

		// Draw Stats
		if(c.drawStats)
			drawStats();

		// Draw Cars
		for(int i=0;i<c.numOfCars;i++)
			cars[i].drawCar(g2d, i);
	}

	private void drawStats() {
		int statHeight=400;
		float statAmplitude=100;
		g2d.setColor(Color.BLACK);
		g2d.drawLine(275, statHeight, 840, statHeight);

		//Speed
		g2d.setColor(Color.PINK);
		int spdHeight=(int) (graphValueIn[0]*statAmplitude);
		g2d.fillRect(310,statHeight-spdHeight, 30,  spdHeight);
		g2d.setColor(Color.BLACK);

		//angle
		g2d.setColor(Color.RED);

		int angHeight=(int) (graphValueIn[1]*statAmplitude);

		if(graphValueIn[1]>0){

			g2d.fillRect(340,statHeight-angHeight, 30,  angHeight);
		}
		else if(graphValueIn[1]<0){
			angHeight*=-1;
			g2d.fillRect(340,statHeight, 30, angHeight);
		}

		g2d.setColor(Color.BLACK);

		//sensor
		int sensorHeight=0;
		for(int i=0;i<=4;i++){
			g2d.setColor(Color.ORANGE);

			sensorHeight=(int) ((graphValueIn[i+2]/1.25f)*statAmplitude);

			g2d.fillRect(370+i*30, statHeight-sensorHeight, 30, sensorHeight);
			g2d.setColor(Color.BLACK);
			g2d.drawString("sen"+i, 370+i*30, statHeight+15);
		}

		//OUTPUT
		Color[] colors={Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.GRAY};
		float avg=0;

		int outHeight=0;

		for(int i=0;i<=2;i++) avg=graphValueOut[i]+avg;
		avg/=3;
		for(int i=0;i<=2;i++){
			g2d.setColor(colors[i]);
			outHeight=(int) ((graphValueOut[i]-avg)*statAmplitude)*15;

			if(outHeight>0.0f){
				g2d.fillRect(580+i*30,statHeight-outHeight, 30,  outHeight);
			}

			else if(outHeight<0.0f){
				outHeight*=-1;
				g2d.fillRect(580+i*30,statHeight, 30, outHeight);
			}
		}

		g2d.setColor(Color.BLACK);

		float avg2=0.0f;
		for(int i=3;i<=5;i++){
			avg2=graphValueOut[i]+avg2;
		}

		avg2/=3;

		for(int i=3;i<=5;i++){
			g2d.setColor(colors[i]);
			outHeight= (int) (((graphValueOut[i]-avg2)*statAmplitude)*15);
			if(outHeight>0.0f){
				g2d.fillRect(580+i*30,statHeight-outHeight, 30,  outHeight);
			}
			else if(outHeight<0.0f){
				outHeight*=-1;
				g2d.fillRect(580+i*30,statHeight, 30, outHeight);
			}
		}
		g2d.setColor(Color.BLACK);
		g2d.setFont(new Font("TimesRoman", Font.PLAIN, 15)); 
		g2d.drawString("spd", 310, statHeight+15);
		g2d.drawString("ang", 340, statHeight+15);
		g2d.drawString("acc", 585, statHeight+15);
		g2d.drawString("dec", 615, statHeight+15);
		g2d.drawString("idl", 645, statHeight+15);
		g2d.drawString("rt",  675, statHeight+15);
		g2d.drawString("lt",  705, statHeight+15);
		g2d.drawString("idl", 735, statHeight+15);
		g2d.drawString("lrn rt", 790, statHeight+15);

		g2d.setColor(Color.RED);
		int learnHeight= g.stopLearn? 0 : (int) ((graphValueLearnRate/c.learnRateBig)*(statAmplitude-30));
		g2d.fillRect(790, statHeight-learnHeight, 30, learnHeight);
		
		// Draw NN image
		g2d.drawImage(image, 310, 420, null);
	}
	
	private void drawButtons() {
		g2d.setColor(Color.BLACK);
		g2d.setFont(new Font("TimesRoman", Font.PLAIN, 15)); 

		g2d.drawRect(c.button0X, c.button0Y, c.buttonLength, c.buttonHeight);
		g2d.drawString("Slow / Fast", c.button0X+20, c.button0Y+30);

		g2d.drawRect(c.button0X, c.button1Y, c.buttonLength, c.buttonHeight);
		g2d.drawString("Clear Blocks", c.button0X+20, c.button1Y+30);

		g2d.drawRect(c.button0X, c.button2Y, c.buttonLength, c.buttonHeight);
		g2d.drawString("Stop Learning", c.button0X+20, c.button2Y+30);

		g2d.drawRect(c.button1X, c.button0Y, c.buttonLength, c.buttonHeight);
		g2d.drawString("   LIDAR", c.button1X+20, c.button0Y+30);

		g2d.drawRect(c.button1X, c.button1Y, c.buttonLength, c.buttonHeight);
		g2d.drawString("LIDAR Values", c.button1X+20, c.button1Y+30);

		g2d.drawRect(c.button1X, c.button2Y, c.buttonLength, c.buttonHeight);
		g2d.drawString("LIDAR Circle", c.button1X+20, c.button2Y+30);
	}
	
	private void drawBlocks() {
		g2d.setColor(Color.WHITE);
		for(int i=0;i<=g.blockIdx-1;i++){
			g2d.fillRect(g.blockX[i], g.blockY[i], c.blockSize, c.blockSize);
		}
	}

	private void drawInfo() {
		g2d.setColor(Color.BLACK);
		g2d.setFont(new Font("TimesRoman", Font.PLAIN, 25)); 
		g2d.drawString("CAR 0 STATUS", 920, 330);
		g2d.drawLine(920, 335, 1090, 335);
		g2d.setFont(new Font("TimesRoman", Font.PLAIN, 20)); 
		g2d.drawString("Iteration: "+iteration, 920, 380);
		g2d.drawString("CarSpeed: "+String.format("%.4f",cars[0].getSpeed()), 920, 430);
		g2d.drawString("CarAngle: "+String.format("%.4f",cars[0].getAngle()), 920, 480);
		g2d.drawString("Reward: "+String.format("%.4f",cars[0].getReward()), 920, 530);
		g2d.drawString("Learn rate : "+String.format("%.4f", neural.learnRate), 920, 580);
		g2d.drawString("Trained : "+g.trained +" @"+trainedIteration, 920, 630);
	}

	private void drawRoad( int roadWidth2) {
		g2d.setColor(Color.BLACK);
		g2d.fillRect(20, 20, 1860, 920);

		g2d.setColor(Color.WHITE);
		g2d.fillRect(20+roadWidth2, 20+roadWidth2, 1860-2*roadWidth2, 920-2*roadWidth2);
	}
	
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//		Buttons
//
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

	public void mouseClicked(MouseEvent e) {  

		int X=e.getX();
		int Y=e.getY();

		// Button Handling
		if((X>c.button0X && X<c.button0X+c.buttonLength)){
			if(Y>c.button0Y && Y<c.button0Y+c.buttonHeight){
				button0();
				return;
			}
			else if(Y>c.button1Y && Y<c.button1Y+c.buttonHeight){
				button1();
				return;
			}
			else if(Y>c.button2Y && Y<c.button2Y+c.buttonHeight){
				button2();
				return;
			}
		}
		else if((X>c.button1X && X<c.button1X+c.buttonLength)){
			if(Y>c.button0Y && Y<c.button0Y+c.buttonHeight){
				button3();
				return;
			}
			else if(Y>c.button1Y && Y<c.button1Y+c.buttonHeight){
				button4();
				return;
			}
			else if(Y>c.button2Y && Y<c.button2Y+c.buttonHeight){
				button5();
				return;

			}
		}

		// Add obstacle blocks
		if(g.blockIdx!=99) {
			g.blockX[g.blockIdx] = e.getX()-c.blockSize/2;
			g.blockY[g.blockIdx] = e.getY()-c.blockSize/2;
			g.blockIdx++;
		}
	}  

	private void button5() {
		g.drawLIDARCircles=!g.drawLIDARCircles;
	}

	private void button4() {
		g.drawLIDARValue=!g.drawLIDARValue;
	}

	private void button3() {
		g.drawLIDAR=!g.drawLIDAR;
	}

	private void button2() {
		// Stop training completely
		iteration = c.max_iteration;
	}

	private void button1() {
		// Clear all obstacle blocks
		g.blockIdx = 0;
	}

	private void button0() {
		g.duringTrainDelay=Math.abs(g.duringTrainDelay-1);
		g.afterTrainDelay=Math.abs(g.afterTrainDelay-1);
	}
	
	public Main() {		
		addMouseListener(this);
	}
	
	public void mouseEntered(MouseEvent e) {}  
	public void mouseExited(MouseEvent e) {}  
	public void mousePressed(MouseEvent e) {}  
	public void mouseReleased(MouseEvent e) {}  
	public void actionPerformed(ActionEvent e) {}


/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
//
//		Learning
//
/////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	
	private static float standardDevLearn(int carNum, float[] currentStatus) {
		//System.out.println();
		for(int i=0;i<=currentStatus.length-1;i++){

			mean[carNum][i]*=inputCounter[carNum];
			mean[carNum][i]+=currentStatus[i];
			inputCounter[carNum]++;
			mean[carNum][i]/=(inputCounter[carNum]);

			numerators[carNum][i]+=Math.pow(mean[carNum][i]-currentStatus[i],2);
			if(inputCounter[carNum]!=1){
				stdDev[carNum][i]=(float) Math.sqrt(numerators[carNum][i]/(float)(inputCounter[carNum]-1));
			}
			else{
				stdDev[carNum][i]=0.0f;
			}
		}

		float stdDevTotal=0;
		for(int i=0;i<=6;i++){
			if(stdDev[carNum][i]!=0.0f){
				stdDevTotal+=((Math.abs(mean[carNum][i]-currentStatus[i]))/stdDev[carNum][i]);
			}
		}

		stdDevTotal/=7.0f;	
		if(stdDevTotal>=2){
			return 1.0f;
		}
		else{
			return(stdDevTotal/2.0f);
		}
	}

	public static void decisionMaking(float [] nnOutput, int carNum) {
		int max_outputAD;
		int max_outputRL;
		if((!g.no_random && (int)(Math.random() * c.max_iteration) > iteration)) {	// random
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
			if(carNum==0) System.out.print("RRR\n");
			if(c.debug) System.out.print("  right");
		} else if(max_outputRL==4) {
			cars[carNum].turnLeft(2.0f);
			maxQRL = nnOutput[4];
			maxNodeRL = 4;
			if(c.debug) System.out.print("  left");
			if(carNum ==0) System.out.print("LLL\n");
		} else {
			maxQRL = nnOutput[5];
			maxNodeRL = 5;
			if(carNum==0) { System.out.print("NNN\n"); }
			if(c.debug) System.out.print("  rl_none");
		}	

		//if(c.td_debug) { System.out.println("maxNodeRL : "+maxNodeRL); }
	}

	public static float [] calcReward(float [] nnTarget, float maxQAD, int maxNodeAD, float maxQRL, int maxNodeRL, int carNum) {

		float reward=0.0f;
		// if(sum of sensor value is >0), small negative reward.
		// if(there is a conflict, big negative reward. Needs to return car to start point.
		for(int i=0;i<c.numOfDetectLine;i++) {
			if(cars[carNum].stati[i+2]>1.0) { 	// conflict
				//	reward = reward -20;
				cars[carNum].collision = true;
				break;
			} else reward = reward -cars[carNum].stati[i+2]*1.0f;		// something there, negative reward
		}

		// if the car advanced, positive reward.
		//		reward = reward + (float)(x-prevX)*1.0f;
		final float distanceFactor = 1.5f;
		float baseAngle = getBaseAngle(cars[carNum].x, cars[carNum].y, carNum);
		float xDiff = cars[carNum].x-cars[carNum].prevX;
		float yDiff = cars[carNum].y-cars[carNum].prevY;

		if(baseAngle==0) 			reward = reward + (float)(xDiff)*distanceFactor;
		else if(baseAngle==90) 		reward = reward + (float)(yDiff)*distanceFactor;
		else if(baseAngle==-90) 	reward = reward - (float)(yDiff)*distanceFactor;
		else if(baseAngle==180) 	reward = reward - (float)(xDiff)*distanceFactor;
		else if(baseAngle==45) 		reward = reward + ((float)(xDiff)*distanceFactor*0.5f + (float)(yDiff)*distanceFactor*0.5f)*1.41f;
		else if(baseAngle==-45) 	reward = reward + ((float)(xDiff)*distanceFactor*0.5f - (float)(yDiff)*distanceFactor*0.5f)*1.41f;
		else if(baseAngle==135) 	reward = reward - ((float)(xDiff)*distanceFactor*0.5f - (float)(yDiff)*distanceFactor*0.5f)*1.41f;
		else if(baseAngle==-135) 	reward = reward - ((float)(xDiff)*distanceFactor*0.5f + (float)(yDiff)*distanceFactor*0.5f)*1.41f;

		// if the car has angle to forward direction, small negative reward.
		float angleDiff = cars[carNum].angleCar-baseAngle;
		if(angleDiff<=-180) angleDiff 		= angleDiff + 360;
		else if(angleDiff>=180) angleDiff 	= angleDiff - 360;

		final float angleFactor = 1.5f;

		reward = reward + angleFactor * (1.5f - (Math.abs(angleDiff) / 30.0f));

		// Summary
		// 	- LIDAR : Always negative reward -5.0f ~ 0.0f
		//	- Moving forward : -1.5f ~ 1.5f
		//  - Angle : -1.5f ~ 1.5f (-90 ~ 90)

		if(reward<-4) reward = -4;
		if(reward>4)  reward = 4;
		reward = reward / 8.0f + 0.5f; 	// 0.0 ~ 1.0
		if(cars[carNum].collision) reward = -4.0f;

		if(c.debug) System.out.printf(" R %.6f",reward);

		nnTarget[maxNodeRL] = c.alpha*reward + c.gamma * maxQRL;
		nnTarget[maxNodeAD] = c.alpha*reward + c.gamma * maxQAD;

		cars[carNum].reward = reward;

		return nnTarget;
	}
}
