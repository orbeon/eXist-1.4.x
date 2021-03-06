/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2001-04 The eXist Project
 *  http://exist-db.org
 *  
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 *  
 *  $Id$
 */
package org.exist.xmldb;

import java.io.File;
import java.net.BindException;
import java.util.Iterator;

import junit.framework.TestCase;
import junit.textui.TestRunner;

import org.exist.storage.DBBroker;
import org.exist.StandaloneServer;
import org.exist.xmldb.concurrent.DBUtils;
import org.mortbay.util.MultiException;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;
import org.xmldb.api.modules.XMLResource;

/**
 * @author ???
 * @author Pierrick Brihaye <pierrick.brihaye@free.fr>
 */
public class StorageStressTest extends TestCase {

	private static StandaloneServer server = null;
	//TODO : why a remote test ?
	protected final static String URI = "xmldb:exist://localhost:8088/xmlrpc";    
	//protected final static String URI = "xmldb:exist://";    
    public final static String DB_DRIVER = "org.exist.xmldb.DatabaseImpl";
    private final static String COLLECTION_NAME = "unit-testing-collection";
    
    private final static String CONFIG =
        "<collection xmlns=\"http://exist-db.org/collection-config/1.0\">" + 
        "   <index xmlns:x=\"http://www.foo.com\" xmlns:xx=\"http://test.com\">" + 
        "       <fulltext default=\"all\" attributes=\"true\">" +  
        "       </fulltext>" + 
        "       <create path=\"//ELEMENT-1/@attribute-1\" type=\"xs:string\"/>" +
        "   </index>" + 
        "</collection>";
    
    private Collection collection = null;
    
    public void testStore() {
    	try {
	        String[] wordList = DBUtils.wordList(collection);
	        long start = System.currentTimeMillis();
	        for (int i = 0; i < 30000; i++) {
	            File f = DBUtils.generateXMLFile(6, 3, wordList, false);
	            System.out.println("Storing file: " + f.getName() + "; size: " + (f.length() / 1024) + "kB");
	            Resource res = collection.createResource("test_" + i, "XMLResource");
	            res.setContent(f);
	            collection.storeResource(res);
	            f.delete();
	        }
	        System.out.println("Indexing took " + (System.currentTimeMillis() - start));
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }	        
    }
    
    protected void setUp() {
    	//Don't worry about closing the server : the shutdown hook will do the job
    	initServer();
        setUpRemoteDatabase();
    }
    
	private void initServer() {
		try {
			if (server == null) {
				server = new StandaloneServer();
				if (!server.isStarted()) {			
					try {				
						System.out.println("Starting standalone server...");
						String[] args = {};
						server.run(args);
						while (!server.isStarted()) {
							Thread.sleep(1000);
						}
					} catch (MultiException e) {
						boolean rethrow = true;
						Iterator i = e.getExceptions().iterator();
						while (i.hasNext()) {
							Exception e0 = (Exception)i.next();
							if (e0 instanceof BindException) {
								System.out.println("A server is running already !");
								rethrow = false;
								break;
							}
						}
						if (rethrow) throw e;
					}
				}
			}
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }			
	}    
    
    protected void setUpRemoteDatabase() {
    	try {
	        Class cl = Class.forName(DB_DRIVER);
	        Database database = (Database) cl.newInstance();
	        database.setProperty("create-database", "true");
	        DatabaseManager.registerDatabase(database);
	        
	        Collection rootCollection = DatabaseManager.getCollection(URI + DBBroker.ROOT_COLLECTION, "admin", null);
	        
	        Collection childCollection = rootCollection.getChildCollection(COLLECTION_NAME);
	        if (childCollection == null) {
	            CollectionManagementService cms = (CollectionManagementService) rootCollection.getService(
	                    "CollectionManagementService", "1.0");
	            this.collection = cms.createCollection(COLLECTION_NAME);
	        } else {
	            this.collection = childCollection;
	        }
	        
                String existHome = System.getProperty("exist.home");
                File existDir = existHome==null ? new File(".") : new File(existHome);
	        File f = new File(existDir,"samples/shakespeare/hamlet.xml");
	        Resource res = collection.createResource("test1.xml", "XMLResource");
	        res.setContent(f);
	        collection.storeResource(res);
	        
	        IndexQueryService idxConf = (IndexQueryService)
	            collection.getService("IndexQueryService", "1.0");
//	        idxConf.configureCollection(CONFIG);
        } catch (Exception e) {            
            fail(e.getMessage()); 
        }	        
    }
    
    public static void main(String[] args) {
        TestRunner.run(StorageStressTest.class);
		//Explicit shutdown for the shutdown hook
		System.exit(0);		
   }
}
