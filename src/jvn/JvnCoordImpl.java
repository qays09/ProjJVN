/***
 * JAVANAISE Implementation
 * JvnServerImpl class
 * Contact: 
 *
 * Authors: 
 */

package jvn;

import java.rmi.Naming;
import java.rmi.registry.LocateRegistry;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Hashtable;
import java.io.Serializable;


public class JvnCoordImpl 	
              extends UnicastRemoteObject 
							implements JvnRemoteCoord{
	
	//"listeObjetsJVN" : Tableau des ObjetsJVN enregistr�s associ�s aux noms symboliques (nom Symbolique, objetJVN)
	private Hashtable<String,JvnObject> listeObjetsJVN;
	
	//"listeLockJVN" : Tableau (id , (objet applicatif , lock, liste des serveurs ayant ce lock))
	private Hashtable<Integer,JvnSerialLock> listeLockJVN;
	
	//Pour associer un identifiant unique aux objets cr��s
	private Integer number;
	
	//nom symbolique pour l'appel distant au coordinateur
	private String name = "CoordName";

  /**
  * Default constructor
  * @throws JvnException
  **/
	private JvnCoordImpl() throws Exception {
		listeObjetsJVN = new Hashtable<String,JvnObject>();
		listeLockJVN = new Hashtable<Integer,JvnSerialLock>();
		number = 0;
		
		LocateRegistry.createRegistry(1099);
		
		//associer objet rmi au nom name
		Naming.rebind(name, this);
	}
	
	public static void main(String argv[]) {
		try {
			//Lancement du coordinateur
			JvnCoordImpl coord = new JvnCoordImpl();
			System.out.println("Coordinateur en marche.");
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 *  Allocate a NEW JVN object id (usually allocated to a 
	 *  newly created JVN object)
	 * @throws java.rmi.RemoteException,JvnException
	 **/
	public int jvnGetObjectId()
			throws java.rmi.RemoteException,jvn.JvnException {
		int id = number;
		number++;
		return id;
	}

	/**
	 * Associate a symbolic name with a JVN object
	 * @param jon : the JVN object name
	 * @param jo  : the JVN object 
	 * @param joi : the JVN object identification
	 * @param js  : the remote reference of the JVNServer
	 * @throws java.rmi.RemoteException,JvnException
	 **/
	public synchronized void jvnRegisterObject(String jon, JvnObject jo, JvnRemoteServer js)
			throws java.rmi.RemoteException,jvn.JvnException{
		
		//Inserer le JVNObject enregistr� avec le nom associ� dans le tableau "listeObjetsJVN"
		listeObjetsJVN.put(jon,jo);
		
		//Inserer (id, objet, lock) dans le tablea "listeLockJVN"
		JvnLock  jlock = new JvnLock(jo,js);
		JvnSerialLock jserialLock = new JvnSerialLock(jo.jvnGetObject(), jlock);
		listeLockJVN.put(((JvnObjectImpl) jo).getId(), jserialLock);

	}

	/**
	 * Get the reference of a JVN object managed by a given JVN server 
	 * @param jon : the JVN object name
	 * @param js : the remote reference of the JVNServer
	 * @throws java.rmi.RemoteException,JvnException
	 **/
	public JvnObject jvnLookupObject(String jon, JvnRemoteServer js)
			throws java.rmi.RemoteException,jvn.JvnException{

		//r�cuperer un JvnObject avec � partir de son nom associ�
		JvnObject objet = listeObjetsJVN.get(jon);
		
		/*  if ( objet != null) {
			  JvnLock jlock = listeLockJVN.get(((JvnObjectImpl)objet).getId()).getJvnLock();
	  }*/
		if ( objet != null) {
			((JvnObjectImpl)objet).setLock(JvnState.NL);
		}
		return objet;
	}

	/**
	 * Get a Read lock on a JVN object managed by a given JVN server 
	 * @param joi : the JVN object identification
	 * @param js  : the remote reference of the server
	 * @return the current JVN object state
	 * @throws java.rmi.RemoteException, JvnException
	 **/
	public synchronized Serializable jvnLockRead(int joi, JvnRemoteServer js)
			throws java.rmi.RemoteException, JvnException{

		JvnLock jlock = listeLockJVN.get(joi).getJvnLock();
		JvnState lock = jlock.getLock();
		if ( lock == JvnState.W ) {
			ArrayList<JvnRemoteServer> serverAvecLock = jlock.getListServer();
			for(JvnRemoteServer s: serverAvecLock){
				if (s != js) {
					JvnSerialLock serialLock = listeLockJVN.get(joi);
					serialLock.setObjet(s.jvnInvalidateWriterForReader(joi));
				}
			}
		}
		jlock.addServer(js);
		listeLockJVN.get(joi).getJvnLock().setLock(JvnState.R);
		return listeLockJVN.get(joi).getObjet();
	}

  /**
  * Get a Write lock on a JVN object managed by a given JVN server 
  * @param joi : the JVN object identification
  * @param js  : the remote reference of the server
  * @return the current JVN object state
  * @throws java.rmi.RemoteException, JvnException
  **/
   public synchronized Serializable jvnLockWrite(int joi, JvnRemoteServer js)
   throws java.rmi.RemoteException, JvnException{
	   
	   JvnLock jlock = listeLockJVN.get(joi).getJvnLock();
	   JvnState lock = jlock.getLock();
	   if ( lock == JvnState.W ) {
		   ArrayList<JvnRemoteServer> serverAvecLock = jlock.getListServer();
		   for(JvnRemoteServer s: serverAvecLock){
			   if (s != js) {
				   JvnSerialLock serialLock = listeLockJVN.get(joi);
				   serialLock.setObjet(s.jvnInvalidateWriter(joi));
			   }
		   }
	   }else if ( lock == JvnState.R ) {
		   ArrayList<JvnRemoteServer> serverAvecLock = jlock.getListServer();
		   for(JvnRemoteServer s: serverAvecLock){
			   if (s != js) {
				   s.jvnInvalidateReader(joi);
			   }
		   }
	   }
	  
	   listeLockJVN.get(joi).getJvnLock().setLock(JvnState.W);
	   jlock.resetServer(js);
	  return listeLockJVN.get(joi).getObjet();
   }

	/**
	* A JVN server terminates
	* @param js  : the remote reference of the server
	* @throws java.rmi.RemoteException, JvnException
	**/
    public void jvnTerminate(JvnRemoteServer js)
	 throws java.rmi.RemoteException, JvnException {
	 // to be completed
    	for (int i =0; i < number;i++) {
    		JvnLock jlock = listeLockJVN.get(i).getJvnLock();
    		jlock.removeServer(js);	
    	}
    	
    }

}

 
