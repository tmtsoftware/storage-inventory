
/*
 ************************************************************************
 *******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
 **************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
 *
 *  (c) 2020.                            (c) 2020.
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
 *
 ************************************************************************
 */

package org.opencadc.tantar.policy;

import org.opencadc.inventory.Artifact;
import org.opencadc.inventory.storage.StorageMetadata;
import org.opencadc.tantar.Reporter;
import org.opencadc.tantar.ValidateEventListener;


public class StorageIsAlwaysRight extends ResolutionPolicy {

    public StorageIsAlwaysRight(final ValidateEventListener validateEventListener, final Reporter reporter) {
        super(validateEventListener, reporter);
    }

    /**
     * Use the logic of this Policy to correct a conflict caused by the two given items.  One of the arguments can
     * be null, but not both.
     *
     * @param artifact        The Artifact to use in deciding.
     * @param storageMetadata The StorageMetadata to use in deciding.
     */
    @Override
    public void resolve(final Artifact artifact, final StorageMetadata storageMetadata) throws Exception {
        if (artifact == null && storageMetadata == null) {
            throw new RuntimeException("BUG: both args to resolve are null");
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName());
        
        if (storageMetadata == null || !storageMetadata.isValid()) {
            if (artifact != null) {
                sb.append(".deleteArtifact");
                sb.append(" Artifact.id=").append(artifact.getID());
                sb.append(" Artifact.uri=").append(artifact.getURI());
            } else {
                sb.append(".noAction");
            }
            if (storageMetadata == null) {
                sb.append(" reason=no-matching-storageLocation");
            } else {
                sb.append(" loc=").append(storageMetadata.getStorageLocation());
                sb.append(" reason=invalid-storageLocation");
            }
            //reporter.report("Removing Unknown Artifact " + artifact.storageLocation + " as per policy.");
            reporter.report(sb.toString());
            if (artifact != null) {
                validateEventListener.delete(artifact);
            }
            return;
        }
        
        //storageMetadata != null && storageMetadata.isValid()
        if (artifact == null) {
            sb.append(".createArtifact");
            sb.append(" Artifact.uri=").append(storageMetadata.artifactURI);
            sb.append(" loc=").append(storageMetadata.getStorageLocation());
            sb.append(" reason=no-matching-artifact");
            //reporter.report("Adding Artifact " + storageMetadata.getStorageLocation() + " as per policy.");
            reporter.report(sb.toString());
            validateEventListener.createArtifact(storageMetadata);
            return;
        }

        // artifact != null && storageMetadata != null && storageMetadata.isValid()
        if (haveDifferentStructure(artifact, storageMetadata)) {
            // Then prefer the Storage Metadata.
            sb.append(".replaceArtifact");
            sb.append(" Artifact.id=").append(artifact.getID());
            sb.append(" Artifact.uri=").append(artifact.getURI());
            sb.append(" loc=").append(storageMetadata.getStorageLocation());
            //reporter.report("Replacing Artifact " + artifact.storageLocation + " as per policy.");
            reporter.report(sb.toString());
            validateEventListener.replaceArtifact(artifact, storageMetadata);
            return;
        }
        
        sb.append(".valid");
        sb.append(" Artifact.id=").append(artifact.getID());
        sb.append(" Artifact.uri=").append(artifact.getURI());
        sb.append(" loc=").append(storageMetadata.getStorageLocation());
        //reporter.report("Storage Metadata " + storageMetadata.getStorageLocation() + " is valid as per policy.");
        reporter.report(sb.toString());
    }
}
