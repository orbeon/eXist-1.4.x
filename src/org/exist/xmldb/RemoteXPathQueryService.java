/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2003-2007 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xmldb;

import org.apache.xmlrpc.XmlRpcException;
import org.exist.source.Source;
import org.exist.xmlrpc.RpcAPI;
import org.exist.xquery.XPathException;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.CompiledExpression;
import org.xmldb.api.base.ErrorCodes;
import org.xmldb.api.base.Resource;
import org.xmldb.api.base.ResourceSet;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.XMLResource;

import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

public class RemoteXPathQueryService implements XPathQueryServiceImpl, XQueryService {

    protected RemoteCollection collection;
	protected HashMap namespaceMappings = new HashMap(5);
	protected HashMap variableDecls = new HashMap();
	protected Properties outputProperties = null;
	protected boolean protectedMode = false;
    
    /**
     * Creates a new <code>RemoteXPathQueryService</code> instance.
     *
     * @param collection a <code>RemoteCollection</code> value
     */
    public RemoteXPathQueryService(RemoteCollection collection) {
        this.collection = collection;
        this.outputProperties = new Properties(collection.properties);
    }

	/**
     * The method <code>query</code>
     *
     * @param query a <code>String</code> value
     * @return a <code>ResourceSet</code> value
     * @exception XMLDBException if an error occurs
     */
    public ResourceSet query(String query)
        throws XMLDBException {
		return query(query, null);
	}
	
    /**
     * The method <code>query</code>
     *
     * @param query a <code>String</code> value
     * @param sortExpr a <code>String</code> value
     * @return a <code>ResourceSet</code> value
     * @exception XMLDBException if an error occurs
     */
    public ResourceSet query(String query, String sortExpr)
        throws XMLDBException {
        try {
        	HashMap optParams = new HashMap();
            if(sortExpr != null)
            	optParams.put(RpcAPI.SORT_EXPR, sortExpr);
            if(namespaceMappings.size() > 0)
            	optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
            if(variableDecls.size() > 0)
            	optParams.put(RpcAPI.VARIABLES, variableDecls);
            optParams.put(RpcAPI.BASE_URI, 
                    outputProperties.getProperty("base-uri", collection.getPath()));
            if (protectedMode)
                optParams.put(RpcAPI.PROTECTED_MODE, collection.getPath());
            List params = new ArrayList(2);
			params.add(query.getBytes("UTF-8"));
			params.add(optParams);
            HashMap result = (HashMap) collection.getClient().execute("queryP", params);
            
            if(result.get(RpcAPI.ERROR) != null)
            	throwException(result);
            
            Object[] resources = (Object[]) result.get("results");
            int handle = -1;
            int hash = -1;
            if(resources != null && resources.length > 0) {
            	handle = ((Integer)result.get("id")).intValue();
                Integer hashValue = (Integer)result.get("hash");
                hash = hashValue == null ? -1 : hashValue.intValue();
            }
            return new RemoteResourceSet( collection, outputProperties, resources, handle, hash );
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe );
        }
    }

    /**
     * The method <code>compile</code>
     *
     * @param query a <code>String</code> value
     * @return a <code>CompiledExpression</code> value
     * @exception XMLDBException if an error occurs
     */
    public CompiledExpression compile(String query) throws XMLDBException {
        try {
            return compileAndCheck(query);
        } catch (XPathException e) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, e.getMessage(), e );
        }
    }
    
    /**
     * The method <code>compileAndCheck</code>
     *
     * @param query a <code>String</code> value
     * @return a <code>CompiledExpression</code> value
     * @exception XMLDBException if an error occurs
     * @exception XPathException if an error occurs
     */
    public CompiledExpression compileAndCheck(String query) throws XMLDBException, XPathException {
        try {
            HashMap optParams = new HashMap();
            if(namespaceMappings.size() > 0)
                optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
            if(variableDecls.size() > 0)
                optParams.put(RpcAPI.VARIABLES, variableDecls);
            optParams.put(RpcAPI.BASE_URI, 
                    outputProperties.getProperty("base-uri", collection.getPath()));
            List params = new ArrayList(2);
            params.add(query.getBytes("UTF-8"));
            params.add(optParams);
            HashMap result = (HashMap) collection.getClient().execute( "compile", params );
            
            if (result.get(RpcAPI.ERROR) != null)
                throwXPathException(result);
            return new RemoteCompiledExpression(query);
        } catch ( XmlRpcException xre ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre );
        } catch ( IOException ioe ) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe );
        }
    }
    
    /**
     * The method <code>throwException</code>
     *
     * @param result
     * @exception XMLDBException if an error occurs
     */
    private void throwException(HashMap result) throws XMLDBException {
		String message = (String)result.get(RpcAPI.ERROR);
		Integer lineInt = (Integer)result.get(RpcAPI.LINE);
		Integer columnInt = (Integer)result.get(RpcAPI.COLUMN);
		int line = lineInt == null ? 0 : lineInt.intValue();
		int column = columnInt == null ? 0 : columnInt.intValue();
		XPathException cause = new XPathException(line, column, message);
		throw new XMLDBException(ErrorCodes.VENDOR_ERROR, message, cause);
	}

    /**
     * The method <code>throwXPathException</code>
     *
     * @param result a <code>Hashtable</code> value
     * @exception XPathException if an error occurs
     */
    private void throwXPathException(HashMap result) throws XPathException {
        String message = (String)result.get(RpcAPI.ERROR);
        Integer lineInt = (Integer)result.get(RpcAPI.LINE);
        Integer columnInt = (Integer)result.get(RpcAPI.COLUMN);
        int line = lineInt == null ? 0 : lineInt.intValue();
        int column = columnInt == null ? 0 : columnInt.intValue();
        throw new XPathException(line, column, message);
    }
    
	/* (non-Javadoc)
     * @see org.exist.xmldb.XQueryService#execute(org.exist.source.Source)
     */
    public ResourceSet execute(Source source)
        throws XMLDBException {
        try {
            String xq = source.getContent();
            return query(xq, null);
        } catch (IOException e) {
            throw new XMLDBException( ErrorCodes.VENDOR_ERROR, e.getMessage(), e );
        }
    }
    
	/**
     * The method <code>query</code>
     *
     * @param res a <code>XMLResource</code> value
     * @param query a <code>String</code> value
     * @return a <code>ResourceSet</code> value
     * @exception XMLDBException if an error occurs
     */
    public ResourceSet query(XMLResource res, String query)
		throws XMLDBException {
			return query(res, query, null);
	}

    /**
     * The method <code>query</code>
     *
     * @param res a <code>XMLResource</code> value
     * @param query a <code>String</code> value
     * @param sortExpr a <code>String</code> value
     * @return a <code>ResourceSet</code> value
     * @exception XMLDBException if an error occurs
     */
    public ResourceSet query(XMLResource res, String query, String sortExpr)
        throws XMLDBException {
        RemoteXMLResource resource = (RemoteXMLResource) res;
        try {
        	HashMap optParams = new HashMap();
        	if(namespaceMappings.size() > 0)
            	optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
            if(variableDecls.size() > 0)
            	optParams.put(RpcAPI.VARIABLES, variableDecls);
        	if(sortExpr != null)
        		optParams.put(RpcAPI.SORT_EXPR, sortExpr);
			optParams.put(RpcAPI.BASE_URI, 
                    outputProperties.getProperty("base-uri", collection.getPath()));
            if (protectedMode)
                optParams.put(RpcAPI.PROTECTED_MODE, collection.getPath());
            List params = new ArrayList(5);
            params.add(query.getBytes("UTF-8"));
            params.add(resource.path.toString());
            if(resource.id == null)
            	params.add("");
            else
            	params.add(resource.id);
            params.add(optParams);
			HashMap result = (HashMap) collection.getClient().execute("queryP", params);
			
			if(result.get(RpcAPI.ERROR) != null)
            	throwException(result);
			
			Object[] resources = (Object[]) result.get("results");
			int handle = -1;
            int hash = -1;
			if(resources != null && resources.length > 0) {
				handle = ((Integer) result.get("id")).intValue();
                hash = ((Integer)result.get("hash")).intValue();
            }
			return new RemoteResourceSet(collection, outputProperties, resources, handle, hash);
        } catch (XmlRpcException xre) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, xre.getMessage(), xre);
        } catch (IOException ioe) {
            throw new XMLDBException(ErrorCodes.VENDOR_ERROR, ioe.getMessage(), ioe);
        }
    }
    
    /**
     * The method <code>queryResource</code>
     *
     * @param resource a <code>String</code> value
     * @param query a <code>String</code> value
     * @return a <code>ResourceSet</code> value
     * @exception XMLDBException if an error occurs
     */
    public ResourceSet queryResource(String resource, String query)
        throws XMLDBException {
    	Resource res = collection.getResource(resource);
    	if(res == null)
    		throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource " + resource + " not found");
    	if(!"XMLResource".equals(res.getResourceType()))
    		throw new XMLDBException(ErrorCodes.INVALID_RESOURCE, "Resource " + resource + 
    				" is not an XML resource");
        return query((XMLResource) res, query);
    }

    /**
     * The method <code>getVersion</code>
     *
     * @return a <code>String</code> value
     * @exception XMLDBException if an error occurs
     */
    public String getVersion()
        throws XMLDBException {
        return "1.0";
    }

    /**
     * The method <code>setCollection</code>
     *
     * @param col a <code>Collection</code> value
     * @exception XMLDBException if an error occurs
     */
    public void setCollection(Collection col)
        throws XMLDBException {
    }

    /**
     * The method <code>getName</code>
     *
     * @return a <code>String</code> value
     * @exception XMLDBException if an error occurs
     */
    public String getName() throws XMLDBException {
        return "XPathQueryService";
    }

    /**
     * The method <code>getProperty</code>
     *
     * @param property a <code>String</code> value
     * @return a <code>String</code> value
     * @exception XMLDBException if an error occurs
     */
    public String getProperty(String property)
        throws XMLDBException {
    	return outputProperties.getProperty(property);
    }

    /**
     * The method <code>setProperty</code>
     *
     * @param property a <code>String</code> value
     * @param value a <code>String</code> value
     * @exception XMLDBException if an error occurs
     */
    public void setProperty(String property, String value)
        throws XMLDBException {
        outputProperties.setProperty(property, value);
    }

    /**
     * The method <code>clearNamespaces</code>
     *
     * @exception XMLDBException if an error occurs
     */
    public void clearNamespaces() throws XMLDBException {
    	namespaceMappings.clear();
    }

    /**
     * The method <code>removeNamespace</code>
     *
     * @param ns a <code>String</code> value
     * @exception XMLDBException if an error occurs
     */
    public void removeNamespace(final String ns)
        throws XMLDBException {
        for(Iterator i = namespaceMappings.values().iterator(); i.hasNext(); ) {
        	if(i.next().equals(ns))
        		i.remove();
        }
    }

    /**
     * The method <code>setNamespace</code>
     *
     * @param prefix a <code>String</code> value
     * @param namespace a <code>String</code> value
     * @exception XMLDBException if an error occurs
     */
    public void setNamespace(String prefix, String namespace)
             throws XMLDBException {
    	if (prefix == null)
    		prefix = "";
        namespaceMappings.put(prefix, namespace);
    }

    /**
     * The method <code>getNamespace</code>
     *
     * @param prefix a <code>String</code> value
     * @return a <code>String</code> value
     * @exception XMLDBException if an error occurs
     */
    public String getNamespace(String prefix)
        throws XMLDBException {
    	if (prefix == null)
    		prefix = "";
        return (String)namespaceMappings.get(prefix);
    }

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XPathQueryServiceImpl#declareVariable(java.lang.String, java.lang.Object)
	 */
	public void declareVariable(String qname, Object initialValue) throws XMLDBException {
		variableDecls.put(qname, initialValue);
	}
    
	/* (non-Javadoc)
	 * @see org.exist.xmldb.XQueryService#execute(org.exist.xmldb.CompiledExpression)
	 */
	public ResourceSet execute(CompiledExpression expression) throws XMLDBException {
		return query(((RemoteCompiledExpression) expression).getQuery());
	}

	/* (non-Javadoc)
	 * @see org.exist.xmldb.XQueryService#execute(org.xmldb.api.modules.XMLResource, org.exist.xmldb.CompiledExpression)
	 */
	public ResourceSet execute(XMLResource res, CompiledExpression expression)
			throws XMLDBException {
		return query(res, ((RemoteCompiledExpression) expression).getQuery());
	}
	
	/* (non-Javadoc)
	 * @see org.exist.xmldb.XQueryService#setXPathCompatibility(boolean)
	 */
	public void setXPathCompatibility(boolean backwardsCompatible) {
		// TODO: not passed
	}

	/** 
	 * Calling this method has no effect. The server loads modules
	 * relative to its own context.
	 * 
	 * @see org.exist.xmldb.XQueryService#setModuleLoadPath(java.lang.String)
	 */
	public void setModuleLoadPath(String path) {		
	}

    /* (non-Javadoc)
     * @see org.exist.xmldb.XQueryService#dump(org.exist.xmldb.CompiledExpression, java.io.Writer)
     */
    public void dump(CompiledExpression expression, Writer writer)
        throws XMLDBException {
        String query = ((RemoteCompiledExpression)expression).getQuery();
        HashMap optParams = new HashMap();
    	if(namespaceMappings.size() > 0)
        	optParams.put(RpcAPI.NAMESPACES, namespaceMappings);
        if(variableDecls.size() > 0)
        	optParams.put(RpcAPI.VARIABLES, variableDecls);
        optParams.put(RpcAPI.BASE_URI, 
                outputProperties.getProperty("base-uri", collection.getPath()));
        List params = new ArrayList(2);
        params.add(query);
        params.add(optParams);
        try {
            String dump = (String)collection.getClient().execute("printDiagnostics", params);
            writer.write(dump);
        } catch (XmlRpcException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        } catch (IOException e) {
            throw new XMLDBException(ErrorCodes.UNKNOWN_ERROR, e.getMessage(), e);
        }
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.XPathQueryServiceImpl#beginProtected()
     */
    public void beginProtected() {
        protectedMode = true;
    }

    /* (non-Javadoc)
     * @see org.exist.xmldb.XPathQueryServiceImpl#endProtected()
     */
    public void endProtected() {
        protectedMode = false;
    }
}
