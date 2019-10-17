import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Random;

public class Neural {
	// Parameters
	static final int numOfLayer=3;    // 3 = 1 hidden, 4 = 2 hidden
	static float learnRate=c.learnRateBig;

	static final int[] numOfNeurons = new int[numOfLayer];
	static float[][] layers;
	static float errorSum=0.0f;
	static float weightChange=0.0f;
	static float delta=0.0f;
	static float[] deltaHidden;
	static float[][][] weights;
	
	static BufferedWriter bw=null;
	static FileWriter fw=null;
	static BufferedWriter bw2=null;
	static FileWriter fw2=null;
	static int errorCnt = 0;
	static float errorAccu = 0.0f;

	public Neural(int inputLayer, int hiddenLayer, int outputLayer) {
		numOfNeurons[0]=inputLayer;
		numOfNeurons[1]=hiddenLayer;
		numOfNeurons[2]=outputLayer;

		layers=new float[numOfLayer][hiddenLayer];

		weights=new float[numOfLayer-1][hiddenLayer][hiddenLayer];
		deltaHidden=new float[hiddenLayer];
	}

	public void readWeights(){ //via file
		String file="weights_in.txt";
		try (BufferedReader br = new BufferedReader(new FileReader(file))) {
			for(int i=0;i<=1;i++){
				for(int j=0;j<=numOfNeurons[1]-1;j++){
					for(int k=0;k<=numOfNeurons[1]-1;k++){
						weights[i][j][k]=Float.parseFloat(br.readLine());
					}
				}
			}
		}
		
		catch (IOException e) {
			e.printStackTrace();
		}
		System.out.println(weights[0][25][3]);
	}
	
	public void writeWeights(){ //via file
		BufferedWriter bw=null;
		FileWriter fw=null;
		try{
			fw = new FileWriter("weights_out.txt");
			bw = new BufferedWriter(fw);
			for(int i=0;i<=1;i++){
				for(int j=0;j<=numOfNeurons[1]-1;j++){
					for(int k=0;k<=numOfNeurons[1]-1;k++){
						bw.write(weights[i][j][k]+"\n");
					}
				}
			}			
			bw.close();
			fw.close();
		} 
		catch(IOException e){
			e.printStackTrace();
		}
	}
	
	public void initWeight(){ //random
		//make random weights
		Random rand=new Random();
		for(int i=0;i<=weights.length-1;i++){
			for(int j=0;j<=weights[0].length-1;j++){
				for(int k=0;k<=weights[0][0].length-1;k++){
					weights[i][j][k]=rand.nextFloat()*0.2f-0.1f;
					//System.out.println("W="+weights[i][j][k]);
				}
			}
		}
		

		
		try {
			if(c.logError) {
				fw2 = new FileWriter("errors.txt");
				bw2 = new BufferedWriter(fw2);
			}
		}
		catch(IOException e){
			e.printStackTrace();
		}
	}

	public void closeLoggingError () {
		try {
			bw2.close();
			fw2.close();
		} 
		catch(IOException e){
			e.printStackTrace();
		}
	}
	
	public static float [] feedforward(float[] input) { // original

		for(int i=0;i<input.length;i++){
			layers[0][i]=input[i];
			//System.out.println(input[i]);
		}

		// print inputs
		// for(int i=0;i<numOfNeurons[0];i++) System.out.println(" I "+layers[0][i]); 

		for(int i=0;i<numOfLayer-1;i++){
			for(int j=0;j<numOfNeurons[i+1];j++){
				layers[i+1][j]=0.0f;	// init
				for(int k=0;k<=numOfNeurons[i]-1;k++) {
					layers[i+1][j]+=layers[i][k]*weights[i][k][j];
				}
				layers[i+1][j]=(float) (1/(1+Math.exp(-1*layers[i+1][j])));
			}
		}
		// print outputs
		//for(int i=0;i<numOfNeurons[2];i++) System.out.println(" O "+layers[2][i]); 
		//System.exit(1);
		return(layers[2]);
	}

	static void backprop(float []target) {
		// System.out.print("\nOW = ");
		errorSum = 0.0f;

		for(int j=0;j<numOfNeurons[2];j++){  	//error calculation
			//errorSum += Math.abs((layers[2][j]-target[j]));
			errorSum += ((layers[2][j]-target[j]) * (layers[2][j]-target[j]));
		}

		for(int i=0;i<numOfNeurons[1];i++){  		//hidden
			deltaHidden[i]=0.0f;
			for(int j=0;j<numOfNeurons[2];j++){  	//output
				delta = (layers[2][j]-target[j])*layers[2][j]*(1-layers[2][j]);
				//	if(j==0) System.out.println("delta1="+delta);
				weightChange = delta*layers[1][i];
				//	if(j==0) System.out.println("weightChagne="+weightChange+" layer[1][i] ="+layers[1][i]);
				deltaHidden[i] += delta*weights[1][i][j];
				//	if(j==0) System.out.print("w1="+weights[1][i][j]+" -> ");
				weights[1][i][j]-=(learnRate*weightChange);
				//   if(j==0) System.out.println(weights[1][i][j]);
			}
			// System.out.print(weights[1][i][0] + " ");
		}
		try {
			if(c.logError) {
				errorAccu = errorAccu + errorSum;
				errorCnt++; 
			//	System.out.println("ErrorCnt "+errorCnt);
				if(errorCnt == c.errorAvg) {
					bw2.write(errorAccu/(float)c.errorAvg+"\n");
					errorCnt= 0;
					errorAccu = 0.0f;
				}
			}
		}
		catch(IOException e){
			e.printStackTrace();
		}
				
		//hidden layer
		for(int i=0;i<numOfNeurons[0];i++){  		//input layers
			for(int j=0;j<numOfNeurons[1];j++){  	// hidden layers
				delta = (deltaHidden[j])*layers[1][j]*(1-layers[1][j]);
				//	if(j==0) System.out.println("delta2="+delta);
				weightChange = delta*layers[0][i];
				//	if(j==0) System.out.print("w2="+weights[0][i][j]+" -> ");
				weights[0][i][j]-=(learnRate*weightChange);
				//	if(j==0) System.out.println(weights[0][i][j]);

			}
		}
	}

}