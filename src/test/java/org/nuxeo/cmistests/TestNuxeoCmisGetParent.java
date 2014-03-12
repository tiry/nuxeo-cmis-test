package org.nuxeo.cmistests;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.Session;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

public class TestNuxeoCmisGetParent
{

  @Rule
  public NuxeoRule _nuxeo = new NuxeoClientServerAtomPubRule();

  @Test
  public void testGetParent()
    throws Exception
  {
    final Session session;
    final ItemIterable<CmisObject> queryResults;
    session = _nuxeo.getCmisSession();
    queryResults = session.queryObjects("cmis:folder", "", true, session.getDefaultContext());

    for (final CmisObject queryResult : queryResults)
    {
      Assert.assertTrue("Query for cmis:folder but a non folder item was returned",
                        queryResult instanceof Folder);

      final Folder folder = (Folder) queryResult;
      if (!folder.isRootFolder() && folder.getParentId() != null && !"".equals(folder.getParentId()))
      {
        Assert.assertFalse(folder.getParents().isEmpty());
      }
    }
  }

}
