package com.meccano;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.CouchbaseBucket;
import com.couchbase.client.java.CouchbaseCluster;
import com.couchbase.client.java.bucket.BucketManager;
import com.meccano.kafka.KafkaBroker;
import com.meccano.microservices.*;
import com.meccano.utils.CBDataGenerator;
import com.meccano.utils.CBconfig;
import com.meccano.utils.ControlThread;
import com.meccano.utils.RequestGenerator;
import org.apache.log4j.Logger;

import java.util.ArrayList;

/**
 * Created by ruben.casado.tejedor on 30/08/2016.
 *
 * Entry point to execute the scenario. The concurrency (number of threads of each microservice) and
 * DB connexion details are hard-coded here. They could be changed to be read as parameter for automating
 * the execution of the process with different configurations.
 *
 */

public class Main {

    //centralized logs
    static Logger log = Logger.getLogger(Main.class.getName());

    // hard-coded parameters
    static int N_ORDER_MANAGEMENT =15;
    static int N_STOCK_VISIBILITY =8;
    static int N_ORDER_FUFILLMENT =13;
    static int N_SOURCING =31;
    static String BUCKET= "mecanno";


    public static void main(String[] args) throws Exception{

        long init = System.currentTimeMillis();
        log.info("START");

        //details for couchbase connection. Localhost default
        CBconfig db = new CBconfig();
        db.bucket=Main.BUCKET;


        /*
        //code for generating fake data in Couchbase
        CBDataGenerator generator = new CBDataGenerator(db);
        generator.createItems(500, 100);
        //generator.createOrders(500);
        generator.close();
        log.info("Random items created");

        */

        //kafka topics creation for the whole scenario
        KafkaBroker kafka = new KafkaBroker();
        kafka.createTopic("OrderManagement");
        kafka.createTopic("StockVisibility");
        kafka.createTopic("OrderFulfillment");
        kafka.createTopic("Sourcing");
        log.info("Kafka topics created");


        //Request
        Thread orderRequests = new Thread (new RequestGenerator(kafka, 500, 100, 20),"OrderRequest");
        orderRequests.start();
        //orderRequests.join();
        log.info("RequestGenerator created");


        //OrderManagement pull
        ArrayList<Thread> orderManagement = new ArrayList<Thread>();
        for (int i=0; i< Main.N_ORDER_MANAGEMENT;i++){
            Thread t= new Thread (new OrderManagement(kafka, db, "out.txt"), "OrderManagement"+i);
            t.start();
            orderManagement.add(t);
        }

        //StockVisibility pull
        ArrayList<Thread> stockVisibility = new ArrayList<Thread>();
        for (int i=0; i< Main.N_STOCK_VISIBILITY;i++){
            Thread t= new Thread(new StockVisibility(kafka,db), "StockVisibility"+i);
            t.start();
            stockVisibility.add(t);
        }

        //OrderFulfillment pull
        ArrayList<Thread> orderFulfillment = new ArrayList<Thread>();
        for (int i=0; i< Main.N_ORDER_FUFILLMENT;i++){
            Thread t= new Thread (new OrderFulfillment(kafka, db), "orderFulfillment"+i);
            t.start();
            orderFulfillment.add(t);
        }

        //Sourcing pull
        ArrayList<Thread> sourcing = new ArrayList<Thread>();
        for (int i=0; i< Main.N_SOURCING;i++){
            Thread t= new  Thread(new SourcingPL(kafka, db));
            //Thread t = new Thread(new SourcingOL(kafka, db));
            t.start();
            //t.join();
            sourcing.add(t);
        }

        //Control thread to kill all threads when all requests are processed
        Thread control= new Thread(new ControlThread(kafka, Main.N_ORDER_MANAGEMENT, Main.N_STOCK_VISIBILITY,
                                                                Main.N_ORDER_FUFILLMENT, Main.N_SOURCING), "ControlThread");
        control.start();
        control.join();

        db.cluster.disconnect();
        log.info("FINISH");
        long total = System.currentTimeMillis() - init;
        log.info("Time: "+ total );


    }

}
