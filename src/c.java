
public class c {
	private c() {}

	// Road Situation - Scenario 1
	public static final int 	roadWidth			=240;
	public static final int 	innerTopLeftX		=20+roadWidth;
	public static final int 	innerTopLeftY		=20+roadWidth;
	public static final int 	innerBotRightX		=20+roadWidth+1860-2*roadWidth;
	public static final int 	innerBotRightY		=20+roadWidth+920-2*roadWidth;

	public static final int 	outerTopLeftX		=20;
	public static final int 	outerTopLeftY		=20;
	public static final int 	outerBotRightX		=1880;
	public static final int 	outerBotRightY		=940;
	
	// Road Situation - Scenario 2
	//	+--------------------------------+  Y0
	//	|                                |
	//	|     +-------+     +-------+    |  Y1
	//	|     |   I0  |     |   I1  |    |
	//	|     +-------+     +-------+    |  Y2
	//	|                                |
	//	|     +-------+     +-------+    |  Y3
	//	|     |   I2  |     |   I3  |    |
	//	|     +-------+     +-------+    |  Y4
	//  |                                |
	//	+--------------------------------+  Y5
	// X0    X1      X2    X3       X4  X5
	
	public static final int 	WALL_X0 			= 20;
	public static final int 	WALL_X1 			= 20+roadWidth;
	public static final int 	WALL_X5 			= outerBotRightX;
	public static final int 	WALL_X4 			= WALL_X5-roadWidth;
	public static final int 	WALL_X3 			= WALL_X4-((WALL_X4-WALL_X1)-roadWidth)/2;
	public static final int 	WALL_X2 			= WALL_X3-roadWidth;

	public static final int 	WALL_Y0 			= 20;
	public static final int 	WALL_Y1 			= 20+roadWidth;
	public static final int 	WALL_Y5 			= outerBotRightY;
	public static final int 	WALL_Y4 			= WALL_Y5-roadWidth;
	public static final int 	WALL_Y3 			= WALL_Y4-((WALL_Y4-WALL_Y1)-roadWidth)/2;
	public static final int 	WALL_Y2 			= WALL_Y3-roadWidth;

	// Learning
	public static final float 	alpha				= 0.95f;
	public static final float 	gamma				= 0.05f;
	public static final float 	learnRateMid 		= 0.0001f; // param1
	public static final float 	learnRateSmall 		= 0.0001f; // param1
	public static boolean 		car2car_enable 		= true;
	public static boolean 		car2block_enable 	= true;
	public static final boolean	tdLearn				= true;
	public static final int		tdLearnPatternNum	= 20;
	public static final int		tdBufferDepth		= tdLearnPatternNum+1;
	public static final float   tdDiscount			= 0.95f;
	public static final float 	learnRateBig   		= 0.0025f; // param1
	public static final int 	max_iteration 		= 3000000;

	// One of them or both should set.
	public static final boolean trainOnLIDAR		= true;
	public static final boolean trainOnSD			= true;
	
	//EXCLUSIVE. Only one should set and Both of trainOnLIDAR and trainOnSD above should set
	public static boolean		LIDARPriority		= false;
	public static boolean		LIDARSDAvg			= false;  
	public static boolean		LIDARSDAdd			= true;  
	
	// Car
	public static final int 	LIDARLength 		= 120;
	public static final int 	numOfHidden 		= 500; 
	public static final int 	numOfDetectLine		= 5;
	public static final float 	maxSpeed			= 1.0f;	
	public static final float 	minSpeed			= 0.5f;
	
	public static final int 	carBaseAngle [][]   = { // first index : car type, second index : current section where car is.
													    // values : base angle at each section. Needs 25 numbers.
														{135, 180, 180, 180, -135,     90, -0, -135, -0, -90,     90, 135, -0, -45, -90,     90, -0, 45, -0, -90,     45, 0, 0, 0, -45},
														{-45, 0, 90, -0, -0,           -90, -0, 90, -0, -0,       -135, 180, 180, -0, -0,    -0, -0, -0, -0, -0,        -0, -0, -0, -0, -0},
														{-0, -0, 0, 0, 90,           -0, -0, -90, -0, 90,       -0, -0, -90, 180, 180,    -0, -0, -0, -0, -0,        -0, -0, -0, -0, -0},
														{-0, -0, -0, -0, -0,           -0, -0, -0, -0, -0,        135, 180, -135, -0, -0,    90, -0, -90, -0, -0,       45, 0, -45, -0, -0},
														{-0, -0, -0, -0, -0,           -0, -0, -0, -0, -0,        -0, -0, 135, 180, -135,    -0, -0, 90, -0, -90,       -0, -0, 45, 0, -45},
														{-45, 0, 0, 0, 45,             -90, -0, -0, -0, 90,       -90, 180, 180, -0, 90,    -0, -0, -90, -0, 90,       -0, -0, -90, 180, 135},
														{135, 180, -135, -0, -0,       90, -0, -90, -0, -0,       135, -0, -90, 180, -135,   90, -0, -0, -0, -90,       45, 0, 45, 0, -45}, 
													 	{-0, -0, 135, 180, -135,       -0, -0, 90, -0, -90,       -0, -0, 135, -45, -45,       -0, -0, 90, 0, -90,        -0, -0, 45, 0, -45},//7
														{135, 180, -135, -0, -0,       90, -0, -90, -0, -0,       135, -0, -45, -0, -0,       90, -0, -90, -0, -0,       45, 0, -45, -0, -0},
														{-45, 0, 0, 0, 45,             -90, -0, -0, -0, 90,       -135, 180, -135, 180, 180,  -0, -0, -0, -0, -0,        -0, -0, -0, -0, -0}, // 9
														{-0, -0, -0, -0, -0,           -0, -0, -0, -0, -0,        0, 0, 45, 0, 45,          -90, -0, -0, -0, 90,       -135, 180, 180, 180, 135},
																											  };

	public static final int     carInitSec []  		= {	// index : car type
														// values : initial section number of new car
														21, 1, 3, 11, 13,              2, 23, 19, 5, 9,           21
													  };
	
	public static final float   carInitAngle []  		= {	// index : car type
														// values : initial angle of new car
													     0.0f, 0.0f, 0.0f, 180.0f, 180.0f,     0.0f, 0.0f, -90.0f, 90.0f, 90.0f,     -90.0f
													     };
	
	public static final float   carInitSpd []  		= {	// index : car type
														// values : initial speed of new car
		     											0.5f, 0.5f, 0.5f, 0.5f, 0.5f,        0.5f, 0.5f, 0.5f, 0.5f, 0.5f,     0.5f
													  };
	
	// Display Control
	public static final boolean drawStats				= true;
	public static final boolean debug 					= false;	
	public static final boolean td_debug 				= false;
	public static final boolean showBaseAngle			= false;
	
	// NN Control
	public static final boolean saveWeightOnSuccess		= true;	
	public static final boolean logError				= false;
	public static final float 	errorAvg				= 10000.0f;
	public static final int 	how_many_pass_for_pass	= 10;
	public static final boolean afterDone				= false;
	
	//Block
	public static final int blockSize					= 50;
	
	//Button
	public static final int button0X					= 1240;
	public static final int button1X					= 1410;
	public static final int button0Y					= 350;
	public static final int button1Y					= 450;
	public static final int button2Y					= 550;
	public static final int buttonLength				= 120;
	public static final int buttonHeight				= 50;
	
	// Parameters may change

	public static final int 	numOfCars 				= 20;
	public static final boolean readWeight				= true;	
}
