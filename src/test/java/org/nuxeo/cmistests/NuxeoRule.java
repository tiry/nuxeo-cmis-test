package org.nuxeo.cmistests;

import static org.nuxeo.cmistests.Helper.createDocument;
import static org.nuxeo.cmistests.Helper.createUserWorkspace;
import static org.nuxeo.cmistests.Helper.getCalendar;
import static org.nuxeo.cmistests.Helper.saveDocument;

import java.net.URI;
import java.util.Calendar;
import java.util.EventListener;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.Servlet;

import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.bindings.CmisBindingFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.RepositoryInfo;
import org.apache.chemistry.opencmis.server.impl.atompub.CmisAtomPubServlet;
import org.apache.chemistry.opencmis.server.shared.BasicAuthCallContextHandler;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.Context;
import org.mortbay.jetty.servlet.ServletHolder;
import org.mortbay.resource.Resource;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.impl.blob.ByteArrayBlob;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.opencmis.impl.server.NuxeoRepositories;
import org.nuxeo.ecm.core.storage.sql.DatabaseHelper;
import org.nuxeo.ecm.core.storage.sql.SQLRepositoryTestCase;
import org.nuxeo.runtime.api.Framework;

public abstract class NuxeoRule extends SQLRepositoryTestCase implements MethodRule
{

  public static final String BASE_RESOURCE = "jetty-test";

  public static final String HTTP_HOST = "localhost";

  public static final int HTTP_PORT = 17488;

  private Server _jettyServer;

  private URI _serverURI;

  public static final String USERNAME = "Administrator";

  public static final String PASSWORD = "test";

  public static final String DELETE_TRANSITION = "delete";

  public static final String FILE1_CONTENT = "Noodles with rice";

  protected Session _cmisSession;

  protected String _rootFolderId;

  @Override
  public Statement apply(final Statement statement, final FrameworkMethod method, final Object object)
  {
    return new Statement()
    {
      @Override
      public void evaluate() throws Throwable
      {
        before();
        try
        {
          statement.evaluate();
        }
        finally
        {
          try
          {
            after();
          }
          catch (final Exception e)
          {
          }
        }
      }
    };
  }

  public void before() throws Exception
  {
    setUpNuxeo();
    setUpJetty();
    openSession();
    setUpCmisSession();
    setUpData();

    final RepositoryInfo repositoryInfo = _cmisSession.getBinding().getRepositoryService().getRepositoryInfo(getRepositoryId(), null);
    _rootFolderId = repositoryInfo.getRootFolderId();
  }

  public void after() throws Exception
  {
    tearDownData();
    tearDownCmisSession();
    tearDownJetty();
    tearDownNuxeo();
  }

  public void setUpNuxeo()
    throws Exception
  {
    super.setUp();

    deployBundle("org.nuxeo.ecm.core.convert.api");
    deployBundle("org.nuxeo.ecm.core.convert");
    deployBundle("org.nuxeo.ecm.core.convert.plugins");
    deployBundle("org.nuxeo.ecm.platform.mimetype.api");
    deployBundle("org.nuxeo.ecm.platform.mimetype.core");
    deployBundle("org.nuxeo.ecm.platform.filemanager.api");
    deployBundle("org.nuxeo.ecm.platform.filemanager.core");
    deployBundle("org.nuxeo.ecm.platform.filemanager.core.listener");
    deployBundle("org.nuxeo.ecm.core.persistence");
    deployBundle("org.nuxeo.ecm.platform.audit.api");
    deployBundle("org.nuxeo.ecm.platform.audit");
    deployBundle("org.nuxeo.ecm.core.opencmis.impl");
    deployBundle("org.nuxeo.ecm.directory.types.contrib");
    deployBundle("org.nuxeo.ecm.platform.login");
    deployBundle("org.nuxeo.ecm.platform.web.common");

    deployBundle("me.meneses.tests.nuxeo");
    deployContrib("me.meneses.tests.nuxeo.test", "OSGI-INF/audit-persistence-config.xml");
  }

  public void tearDownNuxeo()
    throws Exception
  {
    NuxeoRepositories.clear();
    closeSession();
    super.tearDown();
  }

  public void setUpCmisSession() throws Exception
  {
    setUpCmisSession(USERNAME);
  }

  private void setUpCmisSession(final String username) throws Exception
  {
    final SessionFactory sessionFactory = SessionFactoryImpl.newInstance();
    final Map<String, String> cmisParameters = new HashMap<String, String>();

    cmisParameters.put(SessionParameter.AUTHENTICATION_PROVIDER_CLASS,
        CmisBindingFactory.STANDARD_AUTHENTICATION_PROVIDER);

    cmisParameters.put(SessionParameter.CACHE_SIZE_REPOSITORIES, "10");
    cmisParameters.put(SessionParameter.CACHE_SIZE_TYPES, "100");
    cmisParameters.put(SessionParameter.CACHE_SIZE_OBJECTS, "100");

    cmisParameters.put(SessionParameter.REPOSITORY_ID, getRepositoryId());
    cmisParameters.put(SessionParameter.USER, username);
    cmisParameters.put(SessionParameter.PASSWORD, PASSWORD);

    addParams(cmisParameters);

    _cmisSession = sessionFactory.createSession(cmisParameters);
  }

  /** Adds protocol-specific parameters. */
  protected abstract void addParams(Map<String, String> params);

  private void tearDownCmisSession() throws Exception
  {
    if (_cmisSession != null)
    {
      _cmisSession.clear();
      _cmisSession = null;
    }
  }

  private void setUpJetty() throws Exception
  {
    _jettyServer = new Server(HTTP_PORT);

    final Context context = new Context(_jettyServer, "/", Context.SESSIONS);
    context.setBaseResource(Resource.newClassPathResource("/" + BASE_RESOURCE));

    context.setEventListeners(getEventListeners());
    final ServletHolder holder = new ServletHolder(getServlet());
    holder.setInitParameter(CmisAtomPubServlet.PARAM_CALL_CONTEXT_HANDLER, BasicAuthCallContextHandler.class.getName());
    holder.setInitParameter(CmisAtomPubServlet.PARAM_CMIS_VERSION, "1.1");
    context.addServlet(holder, "/*");

    _serverURI = new URI("http://" + HTTP_HOST + ':' + HTTP_PORT + '/');
    _jettyServer.start();
  }

  private void tearDownJetty() throws Exception
  {
    if (_jettyServer != null)
    {
      _jettyServer.stop();
      _jettyServer.join();
    }
  }

  protected abstract EventListener[] getEventListeners();

  protected abstract Servlet getServlet();

  protected abstract Filter getFilter();

  protected URI getServerURI()
  {
    return _serverURI;
  }

  protected void setUpData() throws Exception
  {
    final String workspaceRoot = Helper.createRootWorkspace(session);
    createUserWorkspace(session, workspaceRoot, "Administrator");

    DocumentModel folder1 = new DocumentModelImpl("/", "testfolder1", "Folder");
    folder1.setPropertyValue("dc:title", "testfolder1_Title");
    folder1 = createDocument(session, folder1);

    DocumentModel file1 = new DocumentModelImpl("/testfolder1", "testfile1", "File");
    file1.setPropertyValue("dc:title", "testfile1_Title");
    file1.setPropertyValue("dc:description", "testfile1_description");
    final String content = FILE1_CONTENT;
    final String filename = "testfile.txt";
    final ByteArrayBlob blob1 = new ByteArrayBlob(content.getBytes("UTF-8"), "text/plain");
    blob1.setFilename(filename);
    file1.setPropertyValue("content", blob1);
    final Calendar cal1 = getCalendar(2007, 3, 1, 12, 0, 0);
    file1.setPropertyValue("dc:created", cal1);
    file1.setPropertyValue("dc:modified", cal1);
    file1.setPropertyValue("dc:creator", "michael");
    file1.setPropertyValue("dc:lastContributor", "john");
    file1.setPropertyValue("dc:coverage", "foo/bar");
    file1.setPropertyValue("dc:subjects", new String[]
    { "foo", "gee/moo" });
    file1 = createDocument(session, file1);

    ACPImpl acp;
    ACL acl;
    acl = new ACLImpl();
    acl.add(new ACE("bob", SecurityConstants.BROWSE, true));
    acp = new ACPImpl();
    acp.addACL(acl);
    file1.setACP(acp, true);

    DocumentModel file2 = new DocumentModelImpl("/testfolder1", "testfile2", "File");
    file2.setPropertyValue("dc:title", "testfile2_Title");
    file2.setPropertyValue("dc:description", "something");
    final Calendar cal2 = getCalendar(2007, 4, 1, 12, 0, 0);
    file2.setPropertyValue("dc:created", cal2);
    file2.setPropertyValue("dc:creator", "pete");
    file2.setPropertyValue("dc:contributors", new String[] { "pete", "bob" });
    file2.setPropertyValue("dc:lastContributor", "bob");
    file2.setPropertyValue("dc:coverage", "football");
    file2 = createDocument(session, file2);

    acl = new ACLImpl();
    acl.add(new ACE("bob", SecurityConstants.BROWSE, true));
    acp = new ACPImpl();
    acp.addACL(acl);
    file2.setACP(acp, true);

    DocumentModel file3 = new DocumentModelImpl("/testfolder1", "testfile3", "Note");
    file3.setPropertyValue("note", "this is a note");
    file3.setPropertyValue("dc:title", "testfile3_Title");
    file3.setPropertyValue("dc:description", "testfile3_desc1 testfile3_desc2,  testfile3_desc3");
    file3.setPropertyValue("dc:contributors", new String[] { "bob", "john" });
    file3.setPropertyValue("dc:lastContributor", "john");
    file3 = createDocument(session, file3);

    DocumentModel folder2 = new DocumentModelImpl("/", "testfolder2", "Folder");
    folder2.setPropertyValue("dc:title", "testfolder2_Title");
    folder2 = createDocument(session, folder2);

    DocumentModel folder3 = new DocumentModelImpl("/testfolder2", "testfolder3", "Folder");
    folder3.setPropertyValue("dc:title", "testfolder3_Title");
    folder3 = createDocument(session, folder3);

    DocumentModel folder4 = new DocumentModelImpl("/testfolder2", "testfolder4", "Folder");
    folder4.setPropertyValue("dc:title", "testfolder4_Title");
    folder4 = createDocument(session, folder4);

    DocumentModel file4 = new DocumentModelImpl("/testfolder2/testfolder3", "testfile4", "File");
    file4.setPropertyValue("dc:title", "testfile4_Title");
    file4.setPropertyValue("dc:description", "something");
    file4 = createDocument(session, file4);

    DocumentModel file5 = new DocumentModelImpl("/testfolder1", "testfile5", "File");
    file5.setPropertyValue("dc:title", "title5");
    file5 = createDocument(session, file5);
    file5.followTransition(DELETE_TRANSITION);
    file5 = saveDocument(session, file5);

    session.save();

    Framework.getLocalService(EventService.class).waitForAsyncCompletion();
    DatabaseHelper.DATABASE.sleepForFulltext();
  }

  protected void tearDownData()
  {
  }

  protected CoreSession getCoreSession()
  {
    return session;
  }

  public String getRepositoryId()
  {
    return session.getRepositoryName();
  }

  public String getRootFolderId()
  {
    try
    {
      return session.getRootDocument().getId();
    }
    catch (final ClientException e)
    {
      throw new RuntimeException(e);
    }
  }

  public Session getCmisSession()
  {
    return _cmisSession;
  }
}
