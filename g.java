import java.util.ArrayList;

public class g {
	private g() {}

	public static boolean drawLIDAR			= true;		
	public static boolean drawLIDARValue 	= false;	
	public static boolean drawLIDARCircles 	= false;		


	
	public static boolean stopLearn = false;
	public static boolean trained   = false;
	public static float [] carAngles = new float[c.numOfCars];

	public static float [] carLocationX = new float[c.numOfCars];
	public static float [] carLocationY = new float[c.numOfCars];
	
    public static int [] blockX = new int [100];
    public static int [] blockY = new int [100];
    public static int blockIdx = 0;

	public static int afterTrainDelay =1;
	public static int duringTrainDelay =0;
	
	public static boolean no_random = false;
}
