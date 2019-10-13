import os
import time
from math import cos, sin, pi, floor
from adafruit_rplidar import RPLidar
from easygopigo3 import EasyGoPiGo3

# Set up RPLidar
PORT_NAME = '/dev/ttyUSB0'
lidar = RPLidar(None, PORT_NAME)

angle_margin = 4.0
lidar_angle = [-60.0, -30.0, 0.0, 30.0, 60.0]
lidar_dist = [0.0, 0.0, 0.0, 0.0, 0.0]
lidar_samples = [0.0, 0.0, 0.0, 0.0, 0.0]

def collect_angle(angle, distance):
  if(angle>=180): angle = angle -360
  for i in range(5):
    if(angle < lidar_angle[i]+angle_margin and angle > lidar_angle[i]-angle_margin) :
      lidar_dist[i] = lidar_dist[i] + distance
      lidar_samples[i] = lidar_samples[i] + 1.0
#      print("debug1 : ", lidar_angle[i], " ", lidar_dist[i], " ", distance," ", lidar_samples[i])

def process_data():
  for i in range(5):
    if(lidar_samples[i]!=0) :
      lidar_dist[i] = lidar_dist[i]/lidar_samples[i]
#      print("debug2 : ", lidar_angle[i], " ", lidar_dist[i]," ", lidar_samples[i])
    else :
      lidar_dist[i] = 0.0

def print_data():
  for i in range(5):
    print(lidar_angle[i]," : ", floor(lidar_dist[i]), " ", lidar_samples[i])
  print()

def write_data():
  f = open("lidar.txt", "w")
  for i in range(5):
    f.write(str(floor(lidar_dist[i])))
    f.write("\n")
  f.close()

########### Main ###########

#gpg = EasyGoPiGo3()

#gpg.drive_cm(100) # drive forward for length cm
try :
  for scan in lidar.iter_scans():
    lidar_dist = [0.0, 0.0, 0.0, 0.0, 0.0]
    lidar_samples = [0.0, 0.0, 0.0, 0.0, 0.0]
    for(_, angle, distance) in scan:
      collect_angle(angle, distance)
    process_data()
#    print_data()
    write_data()
#    if(lidar_dist[2]==0):
#    gpg.turn_degrees(90) # rotate 90 degrees to the right
#    time.sleep(1)

except KeyboardInterrupt:
  print('Stoping')
lidar.stop()
lidar.disconnect()

