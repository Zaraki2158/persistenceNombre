import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.io.*;
import java.net.*;
import java.time.*;;


class Worker extends Thread
{
    private int id;
    private int nbThreadsActifs;
    private ArrayList<Integer> intervalle = new ArrayList<Integer>();
    private ArrayList<Thread> threadsActifs;
    private int nbThreads;
    private float utilisation;
    private Socket workerSocket;
    private BufferedReader sisr;
    private PrintWriter sisw;
    private boolean arret;


    public Worker(){ this.id = -1;}

    public Worker(int numWorker,int nbThreads,Socket s)
    {
        this.id = numWorker;
        //aide au calcul de l'utilisation des worker
        this.nbThreads = nbThreads;
        this.nbThreadsActifs = 0;
        this.utilisation = 0;
        //******** 
        this.threadsActifs = new ArrayList<Thread>(nbThreads);
        this.workerSocket = s;
        this.arret = false;

        try{
            sisr = new BufferedReader(new InputStreamReader(workerSocket.getInputStream()));
            sisw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(this.workerSocket.getOutputStream())),true);

        } catch (IOException e) {
            System.err.println("Erreur lors de la communication avec le serveur : " + e.getMessage());}
        Serveur.pww[this.id] = sisw;
        Serveur.brw[this.id] = sisr;
    }

    synchronized public int getNbThreadsActifs() {
        return nbThreadsActifs;
    }
    synchronized public void setNbThreadsActifs(int nbThreadsActifs) {
        this.nbThreadsActifs = nbThreadsActifs;
    }
    synchronized public int getNum()
    {
        return this.id;
    }
    synchronized public int getNbThreads() 
    {
        return this.nbThreads;
    }
    public float getUtilisation() 
    {
        return this.utilisation;
    }
    //update l'utilisation du worker
    public void setUtilisation(int n)
    {
            this.utilisation = ((float)(n) / (float)(nbThreads)) * 100;

    }
    //méthode de remplisage de la liste du worker
    synchronized public void setTache(ArrayList<Integer> a)
    {
        intervalle.addAll(a);
        notifWorker();
    }
    //méthode de notification au worker qu'il doit reprendre le travail
    synchronized public void notifWorker() {
        arret = true;
        notify();
    }

    public synchronized void run() {
        // signaler qu'un worker est prêt
        sisw.println("WORKER READY");
          
        while(true)
        {
            // Attendre des commandes du serveur
            while(!arret){
                try {
                    wait();
                } catch (InterruptedException e) {e.printStackTrace();}
            }

            
            while(arret)
            {
                try {
                    // création des Threads de travail en parallèle
                    for(int i=0;i<getNbThreads();i++)
                    {
                        threadsActifs.add(new Thread(new Runnable() 
                    {
                    public void run() {
                        
                        // tant que la liste n'est pas vide on traite l'élément suivant
                        
                        while (!intervalle.isEmpty()) 
                        {
                            
                            int index = -1;
                            // on synchronise l'accès à la liste pour que plusieurs threads ne traitent pas le même élément
                            synchronized (intervalle) {
                                if (intervalle.isEmpty()) {
                                    // si la liste est vide on sort de la boucle
                                    break;
                                }
                                //on avance dans la liste
                                index = intervalle.remove(0);
                            }
                            
                            int n = index;
                            int res = 0;
                            while(n > 9)
                            {
                                int p = 1;

                                //on multiplie tous les chiffres du nombre
                                while(n != 0 && p != 0)
                                {
                                    //calcul du chiffre des unités
                                    int m = n%10;
                                
                                    //si 0 alors la multiplication est nulle
                                    if(m == 0)
                                    {
                                        p = 0;
                                    }
                                    //si 1 alors la multiplication est inutile
                                    else if(m != 1)
                                    {
                                        p *= m;
                                    }
                                    //décalage des chiffres vers la droite
                                    n /= 10;
                                }
                                //changement du nombre courant
                                n = p;
                                //persistance +1
                                res++;
                            }
                            //envoie des résultats au serveur
                            sisw.println(index+" "+res);
                            Serveur.ajouterResultat(index, res);
                        }

                            
                            }
                        }));
                    }

                // Démarrez les threads
                for(Thread t : threadsActifs)
                {
                    t.start();
                    setUtilisation(nbThreads);
                }
                // Attendere que tous les Threads aient finis leur exécution
                for (Thread t : threadsActifs) 
                {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

            
            } catch (Exception e) {e.printStackTrace();}
            //mise en pause du Thread principal et réinitialisation des Threads
            
            arret = false;
            threadsActifs.clear();
            setUtilisation(nbThreads);
            }
            
        }  
    }
}








class Client extends Thread{
    private Socket clientSocket;
    private BufferedReader sisr;
    private PrintWriter sisw;
    private boolean arret = false;
    private String pseudo;
    private int id;

    public int getNumClient()
    {
        return this.id;
    }


    public Client(int id,Socket s) {
        this.id = id;
        this.clientSocket = s;
        try {
            sisr = new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
            sisw = new PrintWriter(new BufferedWriter(
                new OutputStreamWriter(this.clientSocket.getOutputStream())),true);
        } catch (IOException e) {e.printStackTrace();}
        Serveur.pwc[id] = sisw;

        // le client envoie son pseudo
        try{
        sisw.println("Nom client : ");
        pseudo=sisr.readLine();
        }catch(IOException e){e.printStackTrace();}
    }

    public void run(){
        sisw.println("Vous êtes connecté au serveur en tant que client : "+pseudo+".\nVoici les commandes disponibles :\n1 : Calcul de la persistance d'un nombre\n2 : Calcul de la persistance des nombres sur une intervalle\n3 : Liste des nombres avec la plus grande persistance\n4 : Affichage des statistiques(Moyenne, Médiane, Nb d'occurrences par valeur de persistance)\n5 : Affichage de l'utilisation des Workers\nSTOP : déconnexion client");

        try{

        while (!arret) {

                String str = sisr.readLine();          		// lecture du message
                String valeur1,valeur2 = "";

                switch(str)
                {
                    case "STOP" : arret = true; break; // fermeture de la connexion

                    case "1" :  //calcul de la persistance d'un nombre
                                sisw.println("Nombre : ");
                                do{
                                    valeur1 = sisr.readLine();
                                }
                                while(!valeur1.matches("[0-9]+"));
                                System.out.println("REQUETE : Client : "+pseudo+"    Protocole : "+str+ "    Intervalle : [ "+valeur1+" ; "+valeur1+" ]");
                                Serveur.creerTache(Integer.parseInt(valeur1),Integer.parseInt(valeur1));
                                sisw.println("Résultats stocké dans le fichier texte : Resultats.txt");
                                break;

                    case "2" :  //calcul sur une intervalle
                                sisw.println("Début : ");
                                do{
                                    valeur1 = sisr.readLine();
                                }
                                while(!valeur1.matches("[0-9]+"));
                                sisw.println("Fin : ");
                                do{
                                    valeur2 = sisr.readLine();
                                }
                                while(!valeur2.matches("[0-9]+"));
                                System.out.println("REQUETE : Client : "+pseudo+"    Protocole : "+str+ "    Intervalle : [ "+valeur1+" ; "+valeur2+" ]");
                                Serveur.creerTache(Integer.parseInt(valeur1),Integer.parseInt(valeur2));
                                sisw.println("Résultats stocké dans le fichier texte : Resultats.txt");
                                break;

                    case "3" :  //affichage des nombres avec la plus grande persistance
                                Serveur.affichePlusGrandePersistance(id);
                                break;

                    case "4" :  //affichage de la moyenne/mediane/nb occurrences par valeur de persistance
                                sisw.println("Moyenne / Médiane : ");
                                Serveur.afficheMedianeMoyenne(id);
                                sisw.println("Occurrences par valeur de persistance : ");
                                Serveur.affichePersistance(id);
                                break;

                    case "5" :  // affichage de l'utilisation des workers
                                sisw.println(Serveur.getWorkers());
                                break;
                }
            }
            sisr.close();
            sisw.close();
            clientSocket.close();
        }catch(IOException e){e.printStackTrace();}
    }
}

public class Serveur extends Thread {
    public ServerSocket serverSocket;
    static PrintWriter pwc[];    //ecriture des requetes
    static PrintWriter pww[];    //envoie des requetes
    static BufferedReader brw[];
    static File trace;
    public static Hashtable<Integer,Integer> Resultats;
    public static int[] persistance;
    public static float moyenne;
    public static int plusGrandePersistance;
    public static ArrayList<Integer> NbGrandePersistance;
    public static ConcurrentHashMap<Worker,Float> Workers;
    public static Worker[] Work;
    public static int[] divisionTache;
    static int numWorker = 1;
    private int numClient = 1;
    public static final int DEFAULT_PORT = 8080;
    private static final int MAX_WORKERS = 10;
    private static final int MAX_CLIENTS = 10;
    private static final int MAX_TASKS_PER_WORKER = 10;

    //MODE AUTOMATIQUE : 
    // MIN = nb de départ
    // MAX = nb d'arriver
    // TAILLE_INTERVALLE = taille des intervalles envoyées
    // TEMPS_DATTENTE = temps entre 2 envois de tâches
    private static final int MIN = 0;
    private static final int MAX = 10000000;
    private static final int TAILLE_INTERVALLE = 10000;
    private static final int TEMPS_DATTENTE = 250;
    //Si TRUE alors dès qu'un Worker se connectera, les calculs se lanceront
    //si FALSE alors il faudra que les clients fassent des demandes
    private static boolean AUTOMATIQUE = false;
    //********************

    public Serveur() {
        try {
            //initialisation des attributs et du serveur
            serverSocket = new ServerSocket();
            InetAddress address = InetAddress.getLocalHost();
            serverSocket.bind(new InetSocketAddress(address, DEFAULT_PORT));
            System.out.println(address);
            Workers = new ConcurrentHashMap<Worker, Float>();
            Work = new Worker[MAX_WORKERS];
            Resultats = new Hashtable<Integer, Integer>();
            persistance = new int[11];
            moyenne = 0;
            plusGrandePersistance = 0;
            NbGrandePersistance = new ArrayList<Integer>();
            trace = new File("Resultats.txt");
            if(trace.exists()) trace.delete();
            trace.createNewFile();
            pwc = new PrintWriter[MAX_CLIENTS];
            pww = new PrintWriter[MAX_WORKERS];
            brw = new BufferedReader[MAX_WORKERS];
            System.out.println("SOCKET SERVEUR CREE => "+serverSocket);
        } catch (IOException e) {
            System.err.println("Erreur lors de la création du serveur : " + e.getMessage());
        }
    }

    public void run() {
        while (true) {
            try {
                
                Socket clientSocket = serverSocket.accept();
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                out.println("WORKER ou CLIENT");
                //en attente d'un choix de la nouvelle connexion
                String choix = in.readLine();
                

                if (choix.equals("WORKER") && numWorker<=MAX_WORKERS) {
                    // traitement de la connexion du worker
                    Worker w = new Worker(numWorker, MAX_TASKS_PER_WORKER, clientSocket);
                    Workers.put(w,w.getUtilisation());
                    Work[numWorker-1] = w; 
                    System.out.println("NOUVELLE CONNEXION WORKER - SOCKET => "+clientSocket);
                    numWorker++;                
                    w.start();
                    //mode de calcul automatique
                    if(AUTOMATIQUE)
                    {
                        distributionTache(true);
                        AUTOMATIQUE = false;
                    }
                    
                   
                    
                } else if (choix.equals("CLIENT") && numClient<=MAX_CLIENTS) {
                    // traitement de la connexion du client
                    Client c = new Client(numClient,clientSocket);
                    System.out.println("NOUVELLE CONNEXION CLIENT - SOCKET => "+clientSocket);
                    numClient++;
                    c.start();

                } else if (choix.equals("SHUTDOWN")) {
                    serverSocket.close();
                } else {
                    // commande inconnue
                    out.println("Commande inconnue.");
                }

                
        }catch (IOException e) {
            e.printStackTrace();
            }            
        }
        
    }

    //méthode de création d'une tâche 
    synchronized public static void creerTache(int debut, int fin)
    {
        // si l'intervalle mesure 1
        if(debut == fin ) {
            ArrayList<Integer> a = new ArrayList<>();
            a.add(debut);
            Work[0].setTache(a);
        }
        else {
            divisionTache = new int[numWorker];
            int tailleTache = (fin - debut) / (numWorker-1);
            //la tâche est divisée / nbWorkersPrésents
            for(int i=0 ; i < numWorker ; i++)
            {
                divisionTache[i] = debut + i * tailleTache;
            }
            distributionTache(false);
        }
        
        
    }

    //méthode qui créer un Thread de répartition des tâches
    synchronized public static void distributionTache(boolean auto)
    {
        Thread t = new Thread(new Runnable() {
            public void run() {
        if(!auto)
        {
            if(!Workers.isEmpty() && divisionTache.length > 0)
            {
                boolean b = true;
                while(b)
                {
                    //création et remplissage de la liste de nombre à faire calculer au worker
                        ArrayList<Integer> a = new ArrayList<Integer>();

                        for(int i=0;i<divisionTache.length-1;i++)
                        {
                            for(int j = divisionTache[i] ; j<divisionTache[i+1] ; j++)
                            {
                                a.add(j);
                            }
                            Work[i].setTache(a);
                            a.clear();
                        }
                        divisionTache = new int[numWorker];
                        b = false;
                }
            }        
            if(Workers.isEmpty())
            {
                System.out.println("Aucun WORKER connecté mais requête ajoutée");
            }
       }
       //mode automatique
       else if(auto)
       {

        int cpt1 = TAILLE_INTERVALLE;
        int cpt2 = MIN;

            while(cpt2 < MAX)
            {
                 try {
                    Thread.sleep(TEMPS_DATTENTE);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                        
                //création et répartition des tâches d'après les constantes
                divisionTache = new int[numWorker];
                int tailleTache = (cpt1 - cpt2) / (numWorker-1);
                for(int i=0 ; i < numWorker ; i++)
                {
                    divisionTache[i] = cpt2 + i * tailleTache;
                }
                
                ArrayList<Integer> a = new ArrayList<Integer>();

                //envoie des intervalles
                for(int i=0;i<divisionTache.length-1;i++)
                {
                    for(int j = divisionTache[i] ; j<divisionTache[i+1] ; j++)
                    {
                        a.add(j);
                    }
                    Work[i].setTache(a);
                    a.clear();
                }
                //incrémentation de l'intervalle
                cpt1 += TAILLE_INTERVALLE;
                cpt2 += TAILLE_INTERVALLE;

                
            }

        }
    }});
        

        t.start();
            
    }

    // méthodes utiles aux workers pour envoyer les résultats
    synchronized public static void ajouterResultat(int Nombre, int Persistance)
    {
        //pas d'écriture en double au cas où les Thread se marcheraient dessus
        if(!Resultats.containsKey(Nombre))
        {
            Resultats.put(Nombre,Persistance);
            //statistiques
            persistance[Persistance] += 1 ;
            if(Persistance > plusGrandePersistance) {
                plusGrandePersistance = Persistance;
                NbGrandePersistance.clear();
                NbGrandePersistance.add(Nombre);
            }
            else if(Persistance == plusGrandePersistance) {
                NbGrandePersistance.add(Nombre);
            }
            //libération de mémoire
            if(Resultats.size() > 500);
            {
                ecrireResultat();
            }
            
        }
    }

    // méthode d'écriture des résultats sur le disque
    public static void ecrireResultat()
    {
        if(!Resultats.isEmpty())
        {
            try {
            FileWriter fw = new FileWriter(trace.getAbsolutePath(), true);
            for (Map.Entry<Integer,Integer> r:Resultats.entrySet()) {
                try{
                    fw.write(r.getKey() + ":" + r.getValue()+" / ");
                } catch(IOException e) {e.printStackTrace();}
                
            }
            fw.close();
            //libération de la mémoire
            Resultats.clear();
            } catch (IOException e) {e.printStackTrace();}
        }
        
        
    }

    //méthode de récupération de la liste des nombres avec la plus grande persistance
    public static void affichePlusGrandePersistance(int id)
    {
        Collections.sort(NbGrandePersistance);
        pwc[id].println("Persistance la plus grande : "+plusGrandePersistance);
        for(int i=0;i<NbGrandePersistance.size();i++)
            pwc[id].println(NbGrandePersistance.get(i));
    }

    // méthode d'affichage de la persistance par nombre
    public static void affichePersistance(int id)
    {
        for(int i=0;i<11;i++)
        {
            if(persistance[1]!=0)
            pwc[id].print(i + " : " + persistance[i]+" / ");
            else
            pwc[id].print(i + " :  0 /");
        }
        pwc[id].println();
    }

    // méthode d'affichage de la médiane et de la moyenne
    public static void afficheMedianeMoyenne(int id)
    {
        float nb = 0;
        float mediane = -1;
        for(int i=0;i<persistance.length;i++)
        {
            moyenne += i * persistance[i];
            nb += persistance[i];
        }
        float cpt = 0;
        int k = 0;
        while(mediane == -1)
        {
            cpt += persistance[k];
            if(cpt > nb/2)
            {
                mediane = k;
            }
            k++;
        }

        pwc[id].println(" "+moyenne/nb+"  /  "+mediane);
    }

    // méthode qui renvoit la Hashtable des workers
    public static ConcurrentHashMap<Worker,Float> getWorkers()
    {
        return Workers;
    }


    public static void main(String[] args) {
        Serveur serv = new Serveur();
        serv.run();
    }
}    




