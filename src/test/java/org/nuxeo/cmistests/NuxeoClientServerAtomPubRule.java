package org.nuxeo.cmistests;

import java.util.EventListener;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.Servlet;

import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.server.impl.atompub.CmisAtomPubServlet;
import org.nuxeo.ecm.core.opencmis.bindings.NuxeoCmisContextListener;

public class NuxeoClientServerAtomPubRule extends NuxeoRule
{

  @Override
  protected void addParams(final Map<String, String> params)
  {
    params.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
    params.put(SessionParameter.ATOMPUB_URL, getServerURI().toString());
  }

  @Override
  protected Servlet getServlet()
  {
    return new CmisAtomPubServlet();
  }

  @Override
  protected Filter getFilter()
  {
    return new TrustingNuxeoAuthFilter();
  }

  @Override
  protected EventListener[] getEventListeners()
  {
    return new EventListener[]
    { new NuxeoCmisContextListener() };
  }

}
