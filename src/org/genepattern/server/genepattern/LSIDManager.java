/*
  The Broad Institute
  SOFTWARE COPYRIGHT NOTICE AGREEMENT
  This software and its documentation are copyright (2003-2006) by the
  Broad Institute/Massachusetts Institute of Technology. All rights are
  reserved.

  This software is supplied without any warranty or guaranteed support
  whatsoever. Neither the Broad Institute nor MIT can be responsible for its
  use, misuse, or functionality.
*/


package org.genepattern.server.genepattern;

import java.net.MalformedURLException;
import java.rmi.RemoteException;

import org.genepattern.server.webservice.server.AnalysisJobDataSource;
import org.genepattern.server.webservice.server.dao.AnalysisDataService;
import org.genepattern.server.util.BeanReference;
import org.genepattern.util.LSID;
import org.genepattern.util.LSIDUtil;
import org.genepattern.webservice.OmnigeneException;

/**
 * manage LSID creation and versioning (initially for the repository modules).
 * This manager needs to be able to create new IDs as well as version ids and
 * ensure that a particular ID and version is never given out more than once
 */
public class LSIDManager {


	private static LSIDManager inst = null;

	private static LSIDUtil lsidUtil = LSIDUtil.getInstance();

	private static String initialVersion = "1";

	private LSIDManager() {

	}

	public static LSIDManager getInstance() {
		if (inst == null) {
			inst = new LSIDManager();
		}
		return inst;
	}

	public String getAuthority() {
		return lsidUtil.getAuthority();
	}

	
	public LSID createNewID(String namespace) throws OmnigeneException, RemoteException {
		try {
			LSID newLSID = new LSID(getAuthority(), namespace,
					getNextID(namespace), initialVersion);
			return newLSID;
		} catch (MalformedURLException mue) {
			mue.printStackTrace();
			throw new OmnigeneException("Unable to create new LSID: "
					+ mue.getMessage());
		}
	}

	// get the next ID in the sequence from the DB
	protected synchronized String getNextID(String namespace) throws OmnigeneException,	RemoteException {
		AnalysisDataService ds = getDS();

	// XXX handle suites as well

		int nextId = ds.getNextLSIDIdentifier(namespace);
		return "" + nextId;
	}

	// get the next version for a particular LSID identifier from the DB
	protected synchronized String getNextVersionFromDB(LSID lsid)
			throws OmnigeneException, RemoteException {
		AnalysisDataService ds = getDS();

// XXX handle suits as well
		String nextVersion = ds.getNextLSIDVersion(lsid);
		return nextVersion;
	}

	public LSID getNextIDVersion(String id) throws OmnigeneException,
			RemoteException {
		try {
			LSID anId = new LSID(id);
			LSID nextId = getNextIDVersion(anId);
			return nextId;
		} catch (MalformedURLException mue) {
			mue.printStackTrace();
			return null;
		}
	}

	public LSID getNextIDVersion(LSID lsid) throws OmnigeneException,
			RemoteException, MalformedURLException {
		// go to DB and get the next version for this identifier
		lsid.setVersion(getNextVersionFromDB(lsid));
		return lsid;
	}

	public String getAuthorityType(LSID lsid) {
		return lsidUtil.getAuthorityType(lsid);
	}

	// compare authority types: 1=lsid1 is closer, 0=equal, -1=lsid2 is closer
	// closer is defined as mine > Broad > foreign
	public int compareAuthorities(LSID lsid1, LSID lsid2) {
		return lsidUtil.compareAuthorities(lsid1, lsid2);
	}

	public LSID getNearerLSID(LSID lsid1, LSID lsid2) {
		return lsidUtil.getNearerLSID(lsid1, lsid2); // equal???
	}

	protected AnalysisDataService getDS() throws OmnigeneException {
        AnalysisDataService ds;
		try {
			ds = AnalysisDataService.getInstance();
			return ds;
		} catch (Exception e) {
			throw new OmnigeneException(
					"Unable to find analysisJobDataSource: " + e.getMessage());
		}
	}

}