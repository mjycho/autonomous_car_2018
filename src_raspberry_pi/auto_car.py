import time
import gopigo3
import random
import math
import sys

config_no_motor = 0 
no_object_lidar = 500
currentStatus = [0, 0, 0, 0, 0, 0, 0]
nnOutput = [0, 0, 0, 0, 0, 0]
numOfNeurons = [7,500,6]
numOfLayer = 3
weights = [[[0 for i in range(500)] for i in range(500)] for i in range(2)]
layers = [[0 for i in range(500)] for i in range(3)]
max_speed = 500
min_speed = 100
step_speed = 10 
r_speed = 250
l_speed = 250
dist = [0, 0, 0, 0, 0]
iteration = 0  
current_angle = 0.0
target_angle = 0.0
angle_step = 0.5

def readWeights() :
  filename = "weights_in.txt"
  f =  open(filename,"r+")
  for i in range(0,2):
    for j in range(0,numOfNeurons[1]):
      for k in range(0,numOfNeurons[1]):
        weights[i][j][k]=float(f.readline())
#	print("w ", weights[i][j][k])

def feedforward(input) :
  for i in range(0,len(input)):
    layers[0][i]=input[i]
  for i in range(0,numOfLayer-1):
    for j in range(0,numOfNeurons[i+1]):
      layers[i+1][j]=0
      for k in range(0, numOfNeurons[i]):
        layers[i+1][j]+=layers[i][k]*weights[i][k][j]
      layers[i+1][j]=float((1/(1+math.exp(-1*layers[i+1][j]))))
  return(layers[2][0:6])

def decisionMaking(nnOutput) :
  max_outputRL=0

  if(nnOutput[3]>nnOutput[4]) :
    if(nnOutput[5] > nnOutput[3]) :
      max_outputRL =5;
    else :
      max_outputRL = 3;
  else :
    if(nnOutput[5] > nnOutput[4]):
      max_outputRL =5;
    else :
      max_outputRL = 4;
 
  if(max_outputRL == 3) :
    turnLeft()
  elif(max_outputRL == 4) :
    turnRight()

def read_lidar() :
  f=open("lidar.txt","r")
  lines = f.readlines()
  i = 0
  for line in lines:
#    print(int(line))
    dist[i] = int(line)
    i=i+1
  print()
  f.close()
  return dist

def turnRight() :
  global l_speed
  global r_speed
  global current_angle
  print("RRR")
  if(r_speed > min_speed) :
    r_speed = r_speed - step_speed 
  if(l_speed < max_speed) :
    l_speed = l_speed + step_speed 
  if(current_angle < 30) :
    current_angle = current_angle + angle_step

def turnLeft() :
  global l_speed
  global r_speed
  global current_angle
  print("LLL")
  if(l_speed > min_speed) :
    l_speed = l_speed - step_speed 
  if(r_speed < max_speed) :
    r_speed = r_speed + step_speed 
  if(current_angle > -30) :
    current_angle = current_angle - angle_step

################### Main ##################

if(config_no_motor == 0) :
  GPG = gopigo3.GoPiGo3()

# read weight
readWeights()

# Motor starts at initial speed
if(config_no_motor == 0) :
  GPG.set_motor_dps(GPG.MOTOR_LEFT,  -1*l_speed)
  GPG.set_motor_dps(GPG.MOTOR_RIGHT, -1*r_speed)

currentStatus[0] = 0.5		# speed
currentStatus[1] = 0.0    # angle difference 
currentStatus[2] = 0.0		# -60, 0.0 = no blocking, 1.0 = very close
currentStatus[3] = 0.0		# -30  
currentStatus[4] = 0.0		#  0
currentStatus[5] = 0.0		# 30
currentStatus[6] = 0.0		# 60

while (1) :
  # Lidar
  dist = read_lidar()
  for i in range(5):
    if (dist[i]>no_object_lidar) :
      currentStatus[i+2] = 0.0
    else :
      currentStatus[i+2] = 1.0 - dist[i]/no_object_lidar

  #angle
  currentStatus[1] = (target_angle - current_angle)/180.0

  # feed forward
  nnOutput = feedforward(currentStatus)

  print (*dist)
  print (*currentStatus)
  print (*nnOutput)

  # decision making
  decisionMaking(nnOutput)
 
  # Set new motor speed
  print("r",r_speed," l",l_speed)
  if(config_no_motor == 0) :
    GPG.set_motor_dps(GPG.MOTOR_LEFT,  -1*l_speed)
    GPG.set_motor_dps(GPG.MOTOR_RIGHT, -1*r_speed)

  iteration = iteration + 1
  print ("iteration = ", iteration)

if(config_no_motor == 0) :
  GPG.set_motor_dps(GPG.MOTOR_LEFT,  0)
  GPG.set_motor_dps(GPG.MOTOR_RIGHT, 0)

