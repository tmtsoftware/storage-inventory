/*
************************************************************************
*******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
**************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
*
*  (c) 2021.                            (c) 2021.
*  Government of Canada                 Gouvernement du Canada
*  National Research Council            Conseil national de recherches
*  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
*  All rights reserved                  Tous droits réservés
*
*  NRC disclaims any warranties,        Le CNRC dénie toute garantie
*  expressed, implied, or               énoncée, implicite ou légale,
*  statutory, of any kind with          de quelque nature que ce
*  respect to the software,             soit, concernant le logiciel,
*  including without limitation         y compris sans restriction
*  any warranty of merchantability      toute garantie de valeur
*  or fitness for a particular          marchande ou de pertinence
*  purpose. NRC shall not be            pour un usage particulier.
*  liable in any event for any          Le CNRC ne pourra en aucun cas
*  damages, whether direct or           être tenu responsable de tout
*  indirect, special or general,        dommage, direct ou indirect,
*  consequential or incidental,         particulier ou général,
*  arising from the use of the          accessoire ou fortuit, résultant
*  software.  Neither the name          de l'utilisation du logiciel. Ni
*  of the National Research             le nom du Conseil National de
*  Council of Canada nor the            Recherches du Canada ni les noms
*  names of its contributors may        de ses  participants ne peuvent
*  be used to endorse or promote        être utilisés pour approuver ou
*  products derived from this           promouvoir les produits dérivés
*  software without specific prior      de ce logiciel sans autorisation
*  written permission.                  préalable et particulière
*                                       par écrit.
*
*  This file is part of the             Ce fichier fait partie du projet
*  OpenCADC project.                    OpenCADC.
*
*  OpenCADC is free software:           OpenCADC est un logiciel libre ;
*  you can redistribute it and/or       vous pouvez le redistribuer ou le
*  modify it under the terms of         modifier suivant les termes de
*  the GNU Affero General Public        la “GNU Affero General Public
*  License as published by the          License” telle que publiée
*  Free Software Foundation,            par la Free Software Foundation
*  either version 3 of the              : soit la version 3 de cette
*  License, or (at your option)         licence, soit (à votre gré)
*  any later version.                   toute version ultérieure.
*
*  OpenCADC is distributed in the       OpenCADC est distribué
*  hope that it will be useful,         dans l’espoir qu’il vous
*  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
*  without even the implied             GARANTIE : sans même la garantie
*  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
*  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
*  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
*  General Public License for           Générale Publique GNU Affero
*  more details.                        pour plus de détails.
*
*  You should have received             Vous devriez avoir reçu une
*  a copy of the GNU Affero             copie de la Licence Générale
*  General Public License along         Publique GNU Affero avec
*  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
*  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
*                                       <http://www.gnu.org/licenses/>.
*
************************************************************************
*/

package org.opencadc.minoc;

import ca.nrc.cadc.auth.RunnableAction;
import ca.nrc.cadc.net.HttpDelete;
import ca.nrc.cadc.net.HttpGet;
import ca.nrc.cadc.net.HttpTransfer;
import ca.nrc.cadc.net.HttpUpload;
import ca.nrc.cadc.util.HexUtil;
import ca.nrc.cadc.util.Log4jInit;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.security.MessageDigest;
import javax.security.auth.Subject;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.junit.Assert;
import org.junit.Test;

/**
 *
 * @author pdowler
 */
public class PutTransactionTest extends MinocTest {
    private static final Logger log = Logger.getLogger(PutTransactionTest.class);

    static {
        Log4jInit.setLevel("org.opencadc.minoc", Level.INFO);
    }
    
    public PutTransactionTest() { 
        super();
    }
    
    @Test
    public void testPutCommit() {
        try {
            URI artifactURI = URI.create("cadc:TEST/testPutCommit");
            URL artifactURL = new URL(filesURL + "/" + artifactURI.toString());

            String content = "abcdefghijklmnopqrstuvwxyz";
            byte[] data = content.getBytes();
            
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            final URI expectedChecksum = URI.create("md5:" + HexUtil.toHex(md.digest()));
            final long expectedLength = data.length;
            
            // delete to be safe
            HttpDelete delete = new HttpDelete(artifactURL, false);
            Subject.doAs(userSubject, new RunnableAction(delete));
            
            // put with txn
            InputStream in = new ByteArrayInputStream(data);
            HttpUpload put = new HttpUpload(in, artifactURL);
            put.setRequestProperty(ArtifactAction.PUT_TXN, "true");
            Subject.doAs(userSubject, new RunnableAction(put));
            Assert.assertNull(put.getThrowable());
            Assert.assertEquals("Accepted", 202, put.getResponseCode());
            Assert.assertNotNull("digest", put.getResponseHeader(HttpTransfer.DIGEST));
            Assert.assertNotNull("length", put.getResponseHeader(HttpTransfer.CONTENT_LENGTH));
            String txnID = put.getResponseHeader(ArtifactAction.PUT_TXN);
            Assert.assertNotNull("txnID", txnID);
            log.info("transactionID " + txnID);
            
            URI actualChecksum = put.getDigest();
            long actualLength = put.getContentLength();
            Assert.assertNotNull("contentChecksum", actualChecksum);
            Assert.assertEquals("contentChecksum", expectedChecksum, actualChecksum);
            Assert.assertEquals("contentlength", expectedLength, actualLength);
            
            // head
            HttpGet head = new HttpGet(artifactURL, true);
            head.setHeadOnly(true);
            Subject.doAs(userSubject, new RunnableAction(head));
            Assert.assertEquals("not found", 404, head.getResponseCode());

            // head in txn
            head = new HttpGet(artifactURL, true);
            head.setHeadOnly(true);
            head.setRequestProperty(ArtifactAction.PUT_TXN, txnID);
            Subject.doAs(userSubject, new RunnableAction(head));
            Assert.assertEquals("found transaction", 200, head.getResponseCode());
            Assert.assertEquals("txnID", txnID, head.getResponseHeader(ArtifactAction.PUT_TXN));
            Assert.assertNotNull("digest", head.getResponseHeader(HttpTransfer.DIGEST));
            Assert.assertNotNull("length", head.getResponseHeader(HttpTransfer.CONTENT_LENGTH));
            
            actualChecksum = head.getDigest();
            actualLength = head.getContentLength();
            Assert.assertNotNull("contentChecksum", actualChecksum);
            Assert.assertEquals("contentChecksum", expectedChecksum, actualChecksum);
            Assert.assertEquals("contentlength", expectedLength, actualLength);

            // commit
            in = new ByteArrayInputStream(new byte[0]);
            put = new HttpUpload(in, artifactURL);
            put.setRequestProperty(HttpTransfer.CONTENT_LENGTH, "0");
            put.setRequestProperty(ArtifactAction.PUT_TXN,txnID);
            Subject.doAs(userSubject, new RunnableAction(put));
            Assert.assertNull(put.getThrowable());
            Assert.assertEquals("Created", 201, put.getResponseCode());
            Assert.assertNotNull("digest", put.getResponseHeader(HttpTransfer.DIGEST));
            Assert.assertNotNull("length", put.getResponseHeader(HttpTransfer.CONTENT_LENGTH));
            
            actualChecksum = put.getDigest();
            actualLength = put.getContentLength();
            Assert.assertNotNull("contentChecksum", actualChecksum);
            Assert.assertEquals("contentChecksum", expectedChecksum, actualChecksum);
            Assert.assertEquals("contentlength", expectedLength, actualLength);
            
            // head in txn
            head = new HttpGet(artifactURL, true);
            head.setHeadOnly(true);
            head.setRequestProperty(ArtifactAction.PUT_TXN, txnID);
            Subject.doAs(userSubject, new RunnableAction(head));
            Assert.assertEquals("no more txn", 404, head.getResponseCode());
            
            // head
            head = new HttpGet(artifactURL, true);
            head.setHeadOnly(true);
            Subject.doAs(userSubject, new RunnableAction(head));
            Assert.assertEquals("found committed", 200, head.getResponseCode());
            Assert.assertNull("no txn header", head.getResponseHeader(ArtifactAction.PUT_TXN));
            Assert.assertNotNull("digest", head.getResponseHeader(HttpTransfer.DIGEST));
            Assert.assertNotNull("length", head.getResponseHeader(HttpTransfer.CONTENT_LENGTH));
            
            actualChecksum = head.getDigest();
            actualLength = head.getContentLength();
            Assert.assertNotNull("contentChecksum", actualChecksum);
            Assert.assertEquals("contentChecksum", expectedChecksum, actualChecksum);
            Assert.assertEquals("contentlength", expectedLength, actualLength);
            
            // delete
            delete = new HttpDelete(artifactURL, false);
            Subject.doAs(userSubject, new RunnableAction(delete));
            Assert.assertNull(delete.getThrowable());
            Assert.assertEquals("deleted", 204, delete.getResponseCode());
        } catch (Exception t) {
            log.error("unexpected throwable", t);
            Assert.fail("unexpected throwable: " + t);
        }
    }

    @Test
    public void testPutAbort() {
        try {
            URI artifactURI = URI.create("cadc:TEST/testPutAbort");
            URL artifactURL = new URL(filesURL + "/" + artifactURI.toString());

            String content = "abcdefghijklmnopqrstuvwxyz";
            byte[] data = content.getBytes();

            // delete to be safe
            HttpDelete delete = new HttpDelete(artifactURL, false);
            Subject.doAs(userSubject, new RunnableAction(delete));
            
            // put with txn
            InputStream in = new ByteArrayInputStream(data);
            HttpUpload put = new HttpUpload(in, artifactURL);
            put.setRequestProperty(ArtifactAction.PUT_TXN, "true");
            Subject.doAs(userSubject, new RunnableAction(put));
            Assert.assertNull(put.getThrowable());
            Assert.assertEquals("Accepted", 202, put.getResponseCode());
            String txnID = put.getResponseHeader(ArtifactAction.PUT_TXN);
            Assert.assertNotNull("txnID", txnID);
            log.info("transactionID " + txnID);

            // head
            HttpGet head = new HttpGet(artifactURL, true);
            head.setHeadOnly(true);
            Subject.doAs(userSubject, new RunnableAction(head));
            Assert.assertEquals("not found", 404, head.getResponseCode());

            // head in txn
            head = new HttpGet(artifactURL, true);
            head.setHeadOnly(true);
            head.setRequestProperty(ArtifactAction.PUT_TXN, txnID);
            Subject.doAs(userSubject, new RunnableAction(head));
            Assert.assertEquals("found in txn", 200, head.getResponseCode());
            Assert.assertEquals("txnID", txnID, head.getResponseHeader(ArtifactAction.PUT_TXN));

            // abort
            delete = new HttpDelete(artifactURL, false);
            delete.setRequestProperty(ArtifactAction.PUT_TXN, txnID);
            Subject.doAs(userSubject, new RunnableAction(delete));
            Assert.assertNull(delete.getThrowable());
            Assert.assertEquals("no content", 204, delete.getResponseCode());
            
            // head
            head = new HttpGet(artifactURL, true);
            head.setHeadOnly(true);
            Subject.doAs(userSubject, new RunnableAction(head));
            Assert.assertEquals("not found", 404, head.getResponseCode());
            
            
        } catch (Exception t) {
            log.error("unexpected throwable", t);
            Assert.fail("unexpected throwable: " + t);
        }
    }

    @Test
    public void testPutMultiPartCommit() {
        try {
            URI artifactURI = URI.create("cadc:TEST/testPutMultiPartCommit");
            URL artifactURL = new URL(filesURL + "/" + artifactURI.toString());

            String content1 = "abcdefghijklmnopqrstuvwxyz\n";
            String content2 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ\n";
            String content = content1 + content2;
            
            byte[] data = content.getBytes();
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(data);
            final URI expectedChecksum = URI.create("md5:" + HexUtil.toHex(md.digest()));
            final long expectedLength = data.length;
            
            // delete to be safe
            HttpDelete delete = new HttpDelete(artifactURL, false);
            Subject.doAs(userSubject, new RunnableAction(delete));
            
            // put part 1 with txn
            data = content1.getBytes();
            md.reset();
            md.update(data);
            final URI expectedChecksum1 = URI.create("md5:" + HexUtil.toHex(md.digest()));
            final long expectedLength1 = data.length;
            InputStream in = new ByteArrayInputStream(data);
            HttpUpload put = new HttpUpload(in, artifactURL);
            put.setRequestProperty(ArtifactAction.PUT_TXN, "true");
            Subject.doAs(userSubject, new RunnableAction(put));
            Assert.assertNull(put.getThrowable());
            Assert.assertEquals("Accepted", 202, put.getResponseCode());
            Assert.assertNotNull("digest", put.getResponseHeader(HttpTransfer.DIGEST));
            Assert.assertNotNull("length", put.getResponseHeader(HttpTransfer.CONTENT_LENGTH));
            String txnID = put.getResponseHeader(ArtifactAction.PUT_TXN);
            Assert.assertNotNull("txnID", txnID);
            log.info("transactionID " + txnID);
            
            URI actualChecksum = put.getDigest();
            long actualLength = put.getContentLength();
            Assert.assertNotNull("contentChecksum", actualChecksum);
            Assert.assertEquals("contentChecksum", expectedChecksum1, actualChecksum);
            Assert.assertEquals("contentlength", expectedLength1, actualLength);

            // head
            HttpGet head = new HttpGet(artifactURL, true);
            head.setHeadOnly(true);
            Subject.doAs(userSubject, new RunnableAction(head));
            Assert.assertEquals("not found", 404, head.getResponseCode());

            // head in txn
            head = new HttpGet(artifactURL, true);
            head.setHeadOnly(true);
            head.setRequestProperty(ArtifactAction.PUT_TXN, txnID);
            Subject.doAs(userSubject, new RunnableAction(head));
            Assert.assertEquals("found transaction", 200, head.getResponseCode());
            Assert.assertEquals("txnID", txnID, head.getResponseHeader(ArtifactAction.PUT_TXN));
            Assert.assertNotNull("digest", head.getResponseHeader(HttpTransfer.DIGEST));
            Assert.assertNotNull("length", head.getResponseHeader(HttpTransfer.CONTENT_LENGTH));
            actualChecksum = head.getDigest();
            actualLength = head.getContentLength();
            Assert.assertNotNull("contentChecksum", actualChecksum);
            Assert.assertEquals("contentChecksum", expectedChecksum1, actualChecksum);
            Assert.assertEquals("contentlength", expectedLength1, actualLength);

            // put part 2 with txn
            data = content2.getBytes();
            in = new ByteArrayInputStream(data);
            put = new HttpUpload(in, artifactURL);
            put.setRequestProperty(ArtifactAction.PUT_TXN, txnID);
            Subject.doAs(userSubject, new RunnableAction(put));
            Assert.assertNull(put.getThrowable());
            Assert.assertEquals("Accepted", 202, put.getResponseCode());
            Assert.assertNotNull("digest", put.getResponseHeader(HttpTransfer.DIGEST));
            Assert.assertNotNull("length", put.getResponseHeader(HttpTransfer.CONTENT_LENGTH));
            
            actualChecksum = put.getDigest();
            actualLength = put.getContentLength();
            Assert.assertNotNull("contentChecksum", actualChecksum);
            Assert.assertEquals("contentChecksum", expectedChecksum, actualChecksum);
            Assert.assertEquals("contentlength", expectedLength, actualLength);
            
            // commit
            in = new ByteArrayInputStream(new byte[0]);
            put = new HttpUpload(in, artifactURL);
            put.setRequestProperty(HttpTransfer.CONTENT_LENGTH, "0");
            put.setRequestProperty(ArtifactAction.PUT_TXN,txnID);
            Subject.doAs(userSubject, new RunnableAction(put));
            Assert.assertNull(put.getThrowable());
            Assert.assertEquals("Created", 201, put.getResponseCode());
            Assert.assertNotNull("digest", put.getResponseHeader(HttpTransfer.DIGEST));
            Assert.assertNotNull("length", put.getResponseHeader(HttpTransfer.CONTENT_LENGTH));
            
            actualChecksum = put.getDigest();
            actualLength = put.getContentLength();
            Assert.assertNotNull("contentChecksum", actualChecksum);
            Assert.assertEquals("contentChecksum", expectedChecksum, actualChecksum);
            Assert.assertEquals("contentlength", expectedLength, actualLength);
            
            // head in txn
            head = new HttpGet(artifactURL, true);
            head.setHeadOnly(true);
            head.setRequestProperty(ArtifactAction.PUT_TXN, txnID);
            Subject.doAs(userSubject, new RunnableAction(head));
            Assert.assertEquals("no more txn", 404, head.getResponseCode());
            
            // head
            head = new HttpGet(artifactURL, true);
            head.setHeadOnly(true);
            Subject.doAs(userSubject, new RunnableAction(head));
            Assert.assertEquals("found committed", 200, head.getResponseCode());
            Assert.assertNull("no txn header", head.getResponseHeader(ArtifactAction.PUT_TXN));
            Assert.assertNotNull("digest", head.getResponseHeader(HttpTransfer.DIGEST));
            Assert.assertNotNull("length", head.getResponseHeader(HttpTransfer.CONTENT_LENGTH));
            actualChecksum = head.getDigest();
            actualLength = head.getContentLength();
            Assert.assertNotNull("contentChecksum", actualChecksum);
            Assert.assertEquals("contentChecksum", expectedChecksum, actualChecksum);
            Assert.assertEquals("contentlength", expectedLength, actualLength);
            
            // delete
            delete = new HttpDelete(artifactURL, false);
            Subject.doAs(userSubject, new RunnableAction(delete));
            Assert.assertNull(delete.getThrowable());
            Assert.assertEquals("deleted", 204, delete.getResponseCode());
        } catch (Exception t) {
            log.error("unexpected throwable", t);
            Assert.fail("unexpected throwable: " + t);
        }
    }

}
