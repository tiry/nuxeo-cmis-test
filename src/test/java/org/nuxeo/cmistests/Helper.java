package org.nuxeo.cmistests;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.impl.DocumentModelImpl;
import org.nuxeo.ecm.core.api.security.ACE;
import org.nuxeo.ecm.core.api.security.ACL;
import org.nuxeo.ecm.core.api.security.ACP;
import org.nuxeo.ecm.core.api.security.SecurityConstants;
import org.nuxeo.ecm.core.api.security.impl.ACLImpl;
import org.nuxeo.ecm.core.api.security.impl.ACPImpl;

public class Helper
{

  /**
   * Gets a Calendar object.
   */
  public static GregorianCalendar getCalendar(final int year, final int month, final int day, final int hours,
      final int minutes, final int seconds)
  {
    final TimeZone tz = TimeZone.getDefault();
    return getCalendar(year, month, day, hours, minutes, seconds, tz);
  }

  /**
   * Gets a Calendar object with a specific timezone
   */
  public static GregorianCalendar getCalendar(final int year, final int month, final int day,
      final int hours, final int minutes, final int seconds, final TimeZone tz)
  {
    final GregorianCalendar cal = (GregorianCalendar) Calendar.getInstance(tz);
    cal.set(Calendar.YEAR, year);
    cal.set(Calendar.MONTH, month - 1);
    cal.set(Calendar.DAY_OF_MONTH, day);
    cal.set(Calendar.HOUR_OF_DAY, hours);
    cal.set(Calendar.MINUTE, minutes);
    cal.set(Calendar.SECOND, seconds);
    cal.set(Calendar.MILLISECOND, 0);
    return cal;
  }

  /**
   * For audit, make sure event dates don't have the same millisecond.
   */
  public static void sleepForAuditGranularity() throws InterruptedException
  {
    Thread.sleep(2);
  }

  public static DocumentModel createDocument(final CoreSession session,
      final DocumentModel doc) throws Exception
  {
    sleepForAuditGranularity();
    return session.createDocument(doc);
  }

  public static DocumentModel saveDocument(final CoreSession session,
      final DocumentModel doc) throws Exception
  {
    sleepForAuditGranularity();
    return session.saveDocument(doc);
  }

  public static String createRootWorkspace(final CoreSession repo)
    throws ClientException
  {
    DocumentModel container = new DocumentModelImpl("/", "UserWorkspaceRoot", "UserWorkspaceRoot");
    container = repo.createDocument(container);
    final ACP acp = new ACPImpl();
    final ACL acl = new ACLImpl();
    acl.setACEs(new ACE[]
    { new ACE(SecurityConstants.EVERYONE,
        SecurityConstants.EVERYTHING, false) });
    acp.addACL(acl);
    container.setACP(acp, true);
    return container.getPathAsString();
  }

  public static String createUserWorkspace(final CoreSession repo, final String root, final String username)
    throws ClientException
  {
    DocumentModel ws = new DocumentModelImpl(root, username, "Workspace");
    ws = repo.createDocument(ws);
    final ACP acp = new ACPImpl();
    final ACL acl = new ACLImpl();
    acl.setACEs(new ACE[]
    { new ACE(username, SecurityConstants.EVERYTHING, true) });
    acp.addACL(acl);
    ws.setACP(acp, true);

    repo.save();
    return ws.getPathAsString();
  }
}
