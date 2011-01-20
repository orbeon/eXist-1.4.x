/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2010 The eXist Project
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
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.webdav;

import com.bradmcevoy.http.CollectionResource;


import com.bradmcevoy.http.Auth;
import com.bradmcevoy.http.CopyableResource;
import com.bradmcevoy.http.DeletableResource;
import com.bradmcevoy.http.GetableResource;
import com.bradmcevoy.http.LockInfo;
import com.bradmcevoy.http.LockResult;
import com.bradmcevoy.http.LockTimeout;
import com.bradmcevoy.http.LockToken;
import com.bradmcevoy.http.LockableResource;
import com.bradmcevoy.http.MoveableResource;
import com.bradmcevoy.http.PropFindableResource;
import com.bradmcevoy.http.Range;
import com.bradmcevoy.http.exceptions.BadRequestException;
import com.bradmcevoy.http.exceptions.ConflictException;
import com.bradmcevoy.http.exceptions.LockedException;
import com.bradmcevoy.http.exceptions.NotAuthorizedException;
import com.bradmcevoy.http.exceptions.PreConditionFailedException;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Map;
import javax.xml.transform.TransformerException;

import org.exist.EXistException;
import org.exist.storage.BrokerPool;
import org.exist.security.PermissionDeniedException;
import org.exist.security.User;
import org.exist.util.serializer.XMLWriter;
import org.exist.webdav.ExistResource.Mode;
import org.exist.webdav.exceptions.DocumentAlreadyLockedException;
import org.exist.webdav.exceptions.DocumentNotLockedException;
import org.exist.xmldb.XmldbURI;

/**
 * Class for representing an eXist-db document as a Milton WebDAV document.
 * See <a href="http://milton.ettrema.com">Milton</a>.
 *
 * @author Dannes Wessels (dizzzz_at_exist-db.org)
 */
public class MiltonDocument extends MiltonResource
        implements GetableResource, PropFindableResource,
        DeletableResource, LockableResource, MoveableResource, CopyableResource {

    private ExistDocument existDocument;

    // Only for PROPFIND the estimate size for an XML document must be shown
    private boolean returnContentLenghtAsNull=true;

    /**
     * Set to TRUE if for an XML document an estimated document must be returned. Otherwise
     * for content length NULL is returned.
     */
    public void setReturnContentLenghtAsNull(boolean returnContentLenghtAsNull) {
        this.returnContentLenghtAsNull = returnContentLenghtAsNull;
    }

    /**
     *  Constructor of representation of a Document in the Milton framework, without user information.
     * To be called by the resource factory.
     *
     * @param host  FQ host name including port number.
     * @param uri   Path on server indicating path of resource
     * @param brokerPool Handle to Exist database.
     */
    public MiltonDocument(String host, XmldbURI uri, BrokerPool brokerPool) {
        this(host, uri, brokerPool, null);
    }

    /**
     *  Constructor of representation of a Document in the Milton framework, with user information.
     * To be called by the resource factory.
     *
     * @param host  FQ host name including port number.
     * @param uri   Path on server indicating path of resource.
     * @param user  An Exist operation is performed with  User. Can be NULL.
     * @param pool Handle to Exist database.
     */
    public MiltonDocument(String host, XmldbURI uri, BrokerPool pool, User user) {

        super();

        LOG.debug("DOCUMENT:" + uri.toString());
        resourceXmldbUri = uri;
        brokerPool = pool;
        this.host = host;

        existDocument = new ExistDocument(uri, brokerPool);

        // store simpler type
        existResource = existDocument;

        if (user != null) {
            existDocument.setUser(user);
            existDocument.initMetadata();
        }
    }

    /* ================
     * GettableResource
     * ================ */

    //@Override
    public void sendContent(OutputStream out, Range range, Map<String, String> params, String contentType)
            throws IOException, NotAuthorizedException, BadRequestException {
        try {
            existDocument.stream(out);

        } catch (PermissionDeniedException e) {
            LOG.debug(e.getMessage());
            throw new NotAuthorizedException(this);
        }
    }

    //@Override
    public Long getMaxAgeSeconds(Auth auth) {
        return null;
    }

    //@Override
    public String getContentType(String accepts) {
        return existDocument.getMimeType();
    }

    //@Override
    public Long getContentLength() {
        // Only for PROPFIND the estimate size for an XML document must be shown
        if(returnContentLenghtAsNull && existDocument.isXmlDocument()){
            LOG.debug("Returning NULL for content length XML resource.");
            return null;
        }
        return 0L + existDocument.getContentLength();
    }

    /* ====================
     * PropFindableResource
     * ==================== */

    //@Override
    public Date getCreateDate() {
        Date createDate = null;

        Long time = existDocument.getCreationTime();
        if (time != null) {
            createDate = new Date(time);
        }

        LOG.debug("Create date=" + createDate);

        return createDate;
    }

    /* =================
     * DeletableResource
     * ================= */

    //@Override
    public void delete() throws NotAuthorizedException, ConflictException, BadRequestException {
        existDocument.delete();
    }

    
    /* ================
     * LockableResource
     * ================ */

    //@Override
    public LockResult lock(LockTimeout timeout, LockInfo lockInfo)
            throws NotAuthorizedException, PreConditionFailedException, LockedException {

        org.exist.dom.LockToken inputToken = convertToken(timeout, lockInfo);

        LOG.debug("Lock: " + resourceXmldbUri);
        LockResult lr = null;
        try {
            org.exist.dom.LockToken existLT = existDocument.lock(inputToken);

            // Process result
            LockToken mltonLT = convertToken(existLT);
            lr = LockResult.success(mltonLT);

        } catch (PermissionDeniedException ex) {
            LOG.debug(ex.getMessage());
            throw new NotAuthorizedException(this);

        } catch (DocumentAlreadyLockedException ex) {
            // set result iso throw new LockedException(this);
            LOG.debug(ex.getMessage());
            lr = LockResult.failed(LockResult.FailureReason.ALREADY_LOCKED);

        } catch (EXistException ex) {
            LOG.debug(ex.getMessage());
            lr = LockResult.failed(LockResult.FailureReason.PRECONDITION_FAILED);

        }
        return lr;
    }

    //@Override
    public LockResult refreshLock(String token) throws NotAuthorizedException, PreConditionFailedException {
        
        LOG.debug("Refresh: " + resourceXmldbUri + " token=" + token);

        LockResult lr = null;
        try {
            org.exist.dom.LockToken existLT = existDocument.refreshLock(token);

            // Process result
            LockToken mltonLT = convertToken(existLT);
            lr = LockResult.success(mltonLT);

        } catch (PermissionDeniedException ex) {
            LOG.debug(ex.getMessage());
            throw new NotAuthorizedException(this);

        } catch (DocumentNotLockedException ex) {
            LOG.debug(ex.getMessage());
            lr = LockResult.failed(LockResult.FailureReason.PRECONDITION_FAILED);

        } catch (DocumentAlreadyLockedException ex) {
            //throw new LockedException(this);
            LOG.debug(ex.getMessage());
            lr = LockResult.failed(LockResult.FailureReason.ALREADY_LOCKED);

        } catch (EXistException ex) {
            LOG.debug(ex.getMessage());
            lr = LockResult.failed(LockResult.FailureReason.PRECONDITION_FAILED);

        }
        return lr;
    }

    //@Override
    public void unlock(String tokenId) throws NotAuthorizedException, PreConditionFailedException {

        LOG.debug("Unlock: " + resourceXmldbUri);
        try {
            existDocument.unlock();
        } catch (PermissionDeniedException ex) {
            LOG.debug(ex.getMessage());
            throw new NotAuthorizedException(this);

        } catch (DocumentNotLockedException ex) {
            LOG.debug(ex.getMessage());
            throw new PreConditionFailedException(this);

        } catch (EXistException ex) {
            LOG.debug(ex.getMessage());
            throw new PreConditionFailedException(this);
        }
    }

    //@Override
    public LockToken getCurrentLock() {
        LOG.debug("getLock: " + resourceXmldbUri);
        org.exist.dom.LockToken existLT = existDocument.getCurrentLock();

        if (existLT == null) {
            LOG.debug("No database lock token.");
            return null;
        }

        // Construct Lock Info
        LockToken miltonLT = convertToken(existLT);

        // Return values in Milton object
        return miltonLT;
    }


    /* ================
     * MoveableResource
     * ================ */

    //@Override
    public void moveTo(CollectionResource rDest, String newName) throws ConflictException {
        XmldbURI destCollection = ((MiltonCollection) rDest).getXmldbUri();
        try {
            existDocument.resourceCopyMove(destCollection, newName, Mode.MOVE);

        } catch (EXistException ex) {
            throw new ConflictException(this);
        }
    }


    /* ================
     * CopyableResource
     * ================ */

    //@Override
    public void copyTo(CollectionResource rDest, String newName) {
        XmldbURI destCollection = ((MiltonCollection) rDest).getXmldbUri();
        try {
            existDocument.resourceCopyMove(destCollection, newName, Mode.COPY);

        } catch (EXistException ex) {
            // unable to throw new ConflictException(this);
            LOG.error(ex.getMessage());
        }
    }


    /* ================
     * StAX serializer
     * ================ */
    
    public void writeXML(XMLWriter xw) throws TransformerException {
        xw.startElement("document");
        xw.attribute("name", resourceXmldbUri.lastSegment().toString());
        xw.attribute("created", getXmlDateTime(existDocument.getCreationTime()));
        xw.attribute("last-modified", getXmlDateTime(existDocument.getLastModified()));
        xw.attribute("owner", existDocument.getOwnerUser());
        xw.attribute("group", existDocument.getOwnerGroup());
        xw.attribute("permissions", "" + existDocument.getPermissions().toString());
        xw.attribute("size", "" + existDocument.getContentLength());
        xw.endElement("document");
    }
}
