package com.huawei.controller;

import static org.junit.Assert.assertEquals;

import java.util.*;

import com.huawei.Factory.DataFactory;
import com.huawei.Factory.ServiceFactory;
import com.huawei.Service.CRService;
import com.huawei.Service.CarService;
import com.huawei.Service.CrossService;
import com.huawei.Service.RoadService;
import com.huawei.Status.CarDirection;
import com.huawei.Status.Location;
import com.huawei.Status.RunStatus;
import com.huawei.object.*;

public class CRController2 {
	
	RoadService roadService = null;
	CrossService crossService = null;
	CarService carService = null;
	CRService crService = null;
	private List<Car> mapCar = null;
	private List<Car> waitingCar = null;
	private List<Car> finishedCar = null;
	private List<Car> allFinishedCar = null;
	private List<Car> readyCars = null;
	private Map<Integer, Set<Car>> readyCross = null;
	int time = 1;
	public static CRController2 instance = null;
	int beforeFiniedLength = 0;
	public double weight = 0.4;
	public int maxCarNum ;
	public double maxWeight ;
	public int prorityTim=1;
	private int mapSizeRun = 0;
	public long allScheduleTime = 0;
	
	public List<Car> getMapCar() {
		return mapCar;
	}

	private CRController2(){
		roadService = ServiceFactory.roadService;
		crossService = ServiceFactory.crossService;
		carService = ServiceFactory.carService;
		crService = ServiceFactory.crService;
		mapCar = new ArrayList<Car>(); //地图上的所有车
		waitingCar = new ArrayList<Car>(); //等待本轮调度得车子
		finishedCar = new ArrayList<Car>(); //完成调本轮度得车子
		allFinishedCar = new ArrayList<Car>(); //完成所有路径的车
		readyCars = new ArrayList<Car>();
		readyCross = new HashMap<Integer, Set<Car>>();//车子始发地
	}
	
	public static CRController2 getInstance() {
		if(instance == null) {
			synchronized (CRController2.class) {
				if(instance == null) {
					instance = new CRController2();
				}
			}
		}
		
		return instance;
	}
	
	public void init(List<Car> initCars) {
		clearAll();
		if (initCars != null) {
			mapCar.addAll(initCars);
			readyCars.addAll(initCars);
			updateCarToWaiting();
		}
	}

//	public void setWeight(int weight) {
//		this.weight = weight;
//	}

	public int getMapCarSize(){
		return mapSizeRun;
	}
	/**
	 * 是否完成
	 * @return true为完成, false为未完成
	 */
	public boolean finished() {
		return allFinishedCar.size() == carService.getAllCarSize();
	}
	
	/**
	 * 刚开始初始化的时候需要调用的
	 */
	public void updateCarToWaiting() {
		addAllCarToWaiting();
//		for(Car car: waitingCar) {
//			carService.updateCarStatusBegin(car);
//		}
	}
	
	/**
	 * 地图上加入car序列
	 * @param
	 */
	public void addMapListCar(List<Car> cars) {
		waitingCar.addAll(cars);
		readyCars.addAll(cars);
		mapCar.addAll(cars);
		for(Car car: cars) {
			carService.updateCarStatusBegin(car);
		}
	}
	
	public void testAddMapListCar(List<Car> cars) {
		waitingCar.addAll(cars);
		mapCar.addAll(cars);
	}

	/**
	 *
	 * @param car
	 */
	public void addMapOneCar(Car car) {
		waitingCar.add(car);
		mapCar.add(car);
		readyCars.add(car);
		carService.updateCarStatusBegin(car);
	}
	
	/**
	 * 最终完成的car数量
	 * @return
	 */
	public int getAllFinishedSize() {
		return allFinishedCar.size();
	}
	
	public int getAllTime() {
		return time;
	}
	
	public void update() throws Exception {
		if (readyCars.size() != 0) {
			crossService.updateCrossReadyCars(readyCars);
		}
		
//		if(this.time == 78) {
//			Road road = roadService.getRoadById(6151);
//			List<Channel> fromChannels = road.getFromChannels();
//			List<Channel> toChannels = road.getToChannels();
//			System.out.println("");
//		}
		
		updateRoad();
		
		updateCross();

		Set<Car> readCars = new TreeSet<Car>();
		for(Car car: mapCar) {
			if (car.getCarStatus().getDirection() == CarDirection.NONE) {
				if(car.getCarStatus().getDirection() == CarDirection.NONE && car.getCarStatus().getRunStatus() == RunStatus.FINISHED){
					printLock();
					throw new Exception("dead lock");
				}
				readCars.add(car);
			}
			else if(car.getCarStatus().getDirection() != CarDirection.NONE && car.getCarStatus().getRunStatus() == RunStatus.WAITING) {
				printLock();
				throw new Exception("dead lock");
			}
		}

		mapSizeRun = mapCar.size() - readCars.size();

		updateReadCar(readCars);

		beforeFiniedLength = allFinishedCar.size();
		roadService.updateRoadNowSpeed();
		System.out.println("***update: finishedSize: " + finishedCar.size() + ",MapCarSize: " + (mapCar.size() - readCars.size()));
		initStep();

		if(finished())
			return;

		time++;

	}
	
	public void printLock() {
		Map<Integer, Set<Integer>> crossIdRoadIds = new HashMap<Integer, Set<Integer>>();
		for(Car car: waitingCar) {
			if(car.getCarStatus().getDirection() != CarDirection.NONE) {
//				System.out.println("****" + car);
				Location location = car.getCarStatus().getLocation();
				int roadId = location.getRoadId();
				int toCrossId = location.getToCrossId();
				if(crossIdRoadIds.get(toCrossId) == null) {
					Set<Integer> roadIds = new TreeSet<Integer>();
					roadIds.add(roadId);
					crossIdRoadIds.put(toCrossId, roadIds);
				}else {
					crossIdRoadIds.get(toCrossId).add(roadId);
				}
			}
		}
		System.out.println(crossIdRoadIds);
	}
	
	public void updateRoad() {
		
		int beforeUpdatesize = -1;
		while(beforeUpdatesize != finishedCar.size()) {
			beforeUpdatesize = finishedCar.size();
			roadService.updateAllRoad();
		}
		System.out.println("updateRoad : finishedSize: " + finishedCar.size() + ",MapCarSize: " + mapCar.size() + ",allFinishedSize: " + allFinishedCar.size());
	}
	
	public void updateCross() throws Exception {
		
//		List<Channel> channls_5000_45 = roadService.getChannls(5000, 4, 5);
//		List<Channel> channls_5001_52 = roadService.getChannls(5001, 5, 2);
		
		int beforeUpdatesize = -1;
		Set<Cross> crosses = new TreeSet<Cross>();
		crosses.addAll(crossService.getAllCross());

		for(Cross cross: crosses) {
			updateCarBeginPritory(cross.getId());
		}

		while(beforeUpdatesize != finishedCar.size()) {
			beforeUpdatesize = finishedCar.size();
			for(Cross cross: crosses) {
				int crossId = cross.getId();
				Set<Integer> roadId_toCross = new TreeSet<Integer>();
				roadId_toCross.addAll(cross.getRoadId_toCross());
				int beforeUpdateLength = -1;
//				while(beforeUpdateLength != finishedCar.size()) {
					beforeUpdateLength = finishedCar.size();
//					updateCarBeginPritory(crossId);
					crService.updateCrossRoadProCar(crossId, roadId_toCross);
					for (int roadId : roadId_toCross) {
						if (roadId == -1)
							continue;
						int beforeUpdateSL = -1;
						int preFinishedCarSize = finishedCar.size();
						while (beforeUpdateSL != finishedCar.size()) {
							beforeUpdateSL = finishedCar.size();
							crService.updateRoadByToCrossId(roadId, crossId);
							if (roadService.isJoinCar(roadId, crossId)) {
								updateVipCarByRoadId(roadId, crossId);
							}
						}
					}
//				}
			}
		}
		
//		List<Channel> channls_5000_54 = roadService.getChannls(5000, 5, 4);
//		List<Channel> channls_5018_51 = roadService.getChannls(5018, 5, 1);
//		List<Channel> channls_5001_52 = roadService.getChannls(5001, 5, 2);
//		List<Channel> channls_5010_53 = roadService.getChannls(5010, 5, 3);
//
//		List<Channel> channls_5000_45 = roadService.getChannls(5000, 4, 5);
//		List<Channel> channls_5018_15 = roadService.getChannls(5018, 1, 5);
//		List<Channel> channls_5001_25 = roadService.getChannls(5001, 2, 5);
//		List<Channel> channls_5010_35 = roadService.getChannls(5010, 3, 5);
//
////		channls_5001_52 = roadService.getChannls(5001, 5, 2);
//
//		System.out.println(channls_5001_52);
		
//		for(Car car: finishedCar) {
//			if(car.getTo() == car.getCarStatus().getLocation().getToCrossId())
//				System.out.println(car);
//		}
		System.out.println("updateCross : finishedSize: " + finishedCar.size() + ",MapCarSize: " + mapCar.size() + ",allFinishedSize: " + allFinishedCar.size());
		
		
	}
	
	/**
	 * 通过路口
	 * @param car
	 */
	public void updateThroughCross(Car car, CarStatus nextStatus) {
		
		driveCar(car, nextStatus);
		
	}
	
	public void updateStraThroughCross(Car car) throws Exception {
		CarStatus depStatus = crService.getStraDepStatus(car);
		if(depStatus == null || depStatus.getRunStatus() == RunStatus.WAITING)
			return;
		
		updateThroughCross(car, depStatus);
	}
	
	/**
	 * 左拐通过路口
	 * @param car
	 * @throws Exception 
	 */
	public void updateLeftThroughCross(Car car) throws Exception {
		CarStatus depStatus = crService.getLeftDepStatus(car);
		if(depStatus == null || depStatus.getRunStatus() == RunStatus.WAITING)
			return;
		updateThroughCross(car, depStatus);
	}
	
	/**
	 * 右转通过路口
	 * @param car
	 * @throws Exception 
	 */
	public void updateRightThroughCross(Car car) throws Exception {
		
		CarStatus depStatus = crService.getRightDepStatus(car);
		if(depStatus == null || depStatus.getRunStatus() == RunStatus.WAITING)
			return;
		updateThroughCross(car, depStatus);
	}
	
	public void updateReadCar(Set<Car> readyCars) {

//		if(this.time == 5) {
//			Road road = roadService.getRoadById(6444);
//			List<Channel> fromChannels = road.getFromChannels();
//			List<Channel> toChannels = road.getToChannels();
//			System.out.println("");
//		}

		for(Car car: readyCars) {
			try {
				updateCarBegin(car);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

//		List<Channel> channls_5000_54 = roadService.getChannls(5000, 5, 4);
//		List<Channel> channls_5018_51 = roadService.getChannls(5018, 5, 1);
//		List<Channel> channls_5001_52 = roadService.getChannls(5001, 5, 2);
//		List<Channel> channls_5010_53 = roadService.getChannls(5010, 5, 3);
////
//		List<Channel> channls_5000_45 = roadService.getChannls(5000, 4, 5);
//		List<Channel> channls_5018_15 = roadService.getChannls(5018, 1, 5);
//		List<Channel> channls_5001_25 = roadService.getChannls(5001, 2, 5);
//		List<Channel> channls_5010_35 = roadService.getChannls(5010, 3, 5);
////
//		System.out.println(channls_5000_45);
		
		//System.out.println("updateReadCar : finishedSize: " + finishedCar.size() + ",MapCarSize: " + mapCar.size() + ",allFinishedSize: " + allFinishedCar.size());
	}
	
	public void updateCarBegin(Car car) throws Exception {

		CarStatus depStatus = crService.getBeginDepStatus(car);

		if(maxWeight < car.getWeight())
			maxWeight = car.getWeight();

		if(!judgeNomarlCarBegin(car, depStatus))
			return;
		
		updateCarBegin(car, depStatus);
	}
	
	/**
	 * 更新路口上的优先级车辆
	 * @param crossId
	 * @throws Exception 
	 */
	public void updateCarBeginPritory(int crossId) throws Exception {
		
		Set<Car> readyCars = crossService.getCrossById(crossId).getReadyCars();
		if(readyCars.size() == 0)
			return;
		List<Car> driveCars = new ArrayList<Car>();
		for(Car car: readyCars) {
			CarStatus depStatus = crService.getBeginDepStatus(car);
			if(judgeVipCarBegin(car, depStatus)) {
				updateCarBegin(car, depStatus);
				driveCars.add(car);
				car.setUpdateWay(false);
			}
		}
		if(driveCars.size() != 0) {
			Iterator<Car> it = readyCars.iterator();
			while(it.hasNext()) {
				Car car = it.next();
				if(driveCars.contains(car))
					it.remove();
			}
		}
	}
	
	public void updateVipCarByRoadIdC(int roadId, int crossId) throws Exception {
		Set<Car> readyCars = crossService.getCrossById(crossId).getReadyCars();
		if(readyCars.size() == 0)
			return;
		List<Car> driveCars = new ArrayList<Car>();
		for(Car car: readyCars) {
			CarStatus depStatus = crService.getBeginDepStatus(car);
			if(car.getPlanWayList().get(0) == roadId && judgeVipCarBegin(car, depStatus)) {
				updateCarBegin(car, depStatus);
				driveCars.add(car);
				car.setUpdateWay(false);
			}
		}
		if(driveCars.size() != 0) {
			Iterator<Car> it = readyCars.iterator();
			while(it.hasNext()) {
				Car car = it.next();
				if(driveCars.contains(car))
					it.remove();
			}
		}
	}
	
	public void updateVipCarByRoadId(int roadId, int crossId) throws Exception {
		
		Road road = roadService.getRoadById(roadId);
		int otherCrossId = road.getFrom();
		if(crossId == road.getFrom()) {
			otherCrossId = road.getTo();
			if(road.getDuplex() == 0)
				return;
		}
		
		
//		updateCarBeginPritory(otherCrossId);
		updateVipCarByRoadIdC(roadId, otherCrossId);
	}
	
	/**
	 * 判断Vip车子是否可以出发,不可以出发直接返回
	 * @param car
	 * @param depStatus
	 * @return
	 */
	public boolean judgeVipCarBegin(Car car, CarStatus depStatus) {
		if(depStatus == null || depStatus.getRunStatus() == RunStatus.WAITING) {
			return false;
		}
		
		return true;
	}
	
	/**
	 * 判断普通车子是否可以出发,不可以出发直接变为终止状态
	 * @param car
	 * @param depStatus
	 * @return
	 */
	public boolean judgeNomarlCarBegin(Car car, CarStatus depStatus) {
		if(depStatus == null) {
			updateSpotCarBeginStatus(car);
			return false;
		}
		
		if(depStatus.getRunStatus() == RunStatus.WAITING)
			return false;
		
		return true;
	}
	
	public void updateSpotCarBeginStatus(Car car) {
		car.getCarStatus().setRunStatus(RunStatus.FINISHED);
		driveBeginCar(car);
	}
	
	/**
	 * 更新刚出发的车子状态
	 * @param car
	 * @param depStatus
	 */
	public void updateCarBegin(Car car, CarStatus depStatus) {
		
		mapSizeRun++;
//		car.setPlantime(this.time);
		updateThroughCross(car, depStatus);
	}
	
	public void driveBeginCar(Car car) {
		finishedCar.add(car);
		waitingCar.remove(car);
	}
	
	/**
	 * 开车，清空当前车位信息，更新T+1时间的车位信息，更新车子的状态信息
	 * @param
	 * @param
	 * @param car
	 * @throws Exception
	 */
	public void driveCar(Car car, CarStatus nextStatus){
		
		if(nextStatus.getisFinished()) {
			if(car.getPriority() == 1 && prorityTim < this.time)
				this.prorityTim = this.time;

			allScheduleTime+=(this.time - car.getRealtime());
			car.setRealArriveTime(this.time - car.getRealtime());
			
			mapCar.remove(car);
			allFinishedCar.add(car);
		}
		finishedCar.add(car);
		waitingCar.remove(car);
		crService.driveCar(car, nextStatus);
	}
	
	private void addAllCarToWaiting() {
		waitingCar.addAll(mapCar);
	}
	
	private void initStep() {
		finishedCar.clear();
		waitingCar.clear();
		readyCars.clear();
		for(Car car: mapCar)
			car.getCarStatus().setRunStatus(RunStatus.WAITING);
		addAllCarToWaiting();
	}
	
	public void clearAll(){
		mapCar.clear();
		waitingCar.clear();
		finishedCar.clear();
		allFinishedCar.clear();
		readyCars.clear();
		beforeFiniedLength = 0;
		time = 1;
		mapSizeRun = 0;
	}
	
}
