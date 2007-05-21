/******************************************************************************
 * EvalAssignsLogicImplTest.java - created by aaronz@vt.edu on Dec 28, 2006
 * 
 * Copyright (c) 2007 Virginia Polytechnic Institute and State University
 * Licensed under the Educational Community License version 1.0
 * 
 * A copy of the Educational Community License has been included in this 
 * distribution and is available at: http://www.opensource.org/licenses/ecl1.php
 * 
 * Contributors:
 * Aaron Zeckoski (aaronz@vt.edu) - primary
 * 
 *****************************************************************************/

package org.sakaiproject.evaluation.logic.test;

import java.util.Date;
import java.util.List;

import junit.framework.Assert;

import org.easymock.MockControl;
import org.sakaiproject.evaluation.dao.EvaluationDao;
import org.sakaiproject.evaluation.logic.EvalEmailsLogic;
import org.sakaiproject.evaluation.logic.impl.EvalAssignsLogicImpl;
import org.sakaiproject.evaluation.logic.test.stubs.EvalExternalLogicStub;
import org.sakaiproject.evaluation.model.EvalAssignGroup;
import org.sakaiproject.evaluation.model.EvalEvaluation;
import org.sakaiproject.evaluation.model.EvalScale;
import org.sakaiproject.evaluation.model.constant.EvalConstants;
import org.sakaiproject.evaluation.test.EvalTestDataLoad;
import org.sakaiproject.evaluation.test.PreloadTestData;
import org.springframework.test.AbstractTransactionalSpringContextTests;


/**
 * Test class for EvalAssignsLogicImpl
 *
 * @author Aaron Zeckoski (aaronz@vt.edu)
 */
public class EvalAssignsLogicImplTest extends AbstractTransactionalSpringContextTests {

	protected EvalAssignsLogicImpl assigns;

	private EvaluationDao evaluationDao;
	private EvalTestDataLoad etdl;
	
	private EvalEmailsLogic emails;
	private MockControl emailsControl;

	protected String[] getConfigLocations() {
		// point to the needed spring config files, must be on the classpath
		// (add component/src/webapp/WEB-INF to the build path in Eclipse),
		// they also need to be referenced in the project.xml file
		return new String[] {"hibernate-test.xml", "spring-hibernate.xml"};
	}

	// run this before each test starts
	protected void onSetUpBeforeTransaction() throws Exception {
		// load the spring created dao class bean from the Spring Application Context
		evaluationDao = (EvaluationDao) applicationContext.getBean("org.sakaiproject.evaluation.dao.EvaluationDao");
		if (evaluationDao == null) {
			throw new NullPointerException("EvaluationDao could not be retrieved from spring context");
		}

		// check the preloaded data
		Assert.assertTrue("Error preloading data", evaluationDao.countAll(EvalScale.class) > 0);

		// check the preloaded test data
		Assert.assertTrue("Error preloading test data", evaluationDao.countAll(EvalEvaluation.class) > 0);

		PreloadTestData ptd = (PreloadTestData) applicationContext.getBean("org.sakaiproject.evaluation.test.PreloadTestData");
		if (ptd == null) {
			throw new NullPointerException("PreloadTestData could not be retrieved from spring context");
		}

		// get test objects
		etdl = ptd.getEtdl();

		// load up any other needed spring beans

		// setup the mock objects if needed
		emailsControl = MockControl.createControl(EvalEmailsLogic.class);
		emails = (EvalEmailsLogic) emailsControl.getMock();
		
		//this mock object is simply keeping us from getting a null when emails is accessed 
		emails.sendEvalAvailableGroupNotification(EvalTestDataLoad.INVALID_LONG_ID, EvalTestDataLoad.CONTEXT2); // expect this to be called
		emailsControl.setDefaultMatcher(MockControl.ALWAYS_MATCHER);
		emailsControl.setReturnValue(EvalTestDataLoad.EMPTY_STRING_ARRAY, MockControl.ZERO_OR_MORE);
		emailsControl.replay();

		//create and setup the object to be tested
		assigns = new EvalAssignsLogicImpl();
		assigns.setDao(evaluationDao);
		assigns.setExternalLogic( new EvalExternalLogicStub() );
		assigns.setEmails(emails); // set to the mock object
	}

	// run this before each test starts and as part of the transaction
	protected void onSetUpInTransaction() {
		// preload additional data if desired
		
	}

	/**
	 * ADD unit tests below here, use testMethod as the name of the unit test,
	 * Note that if a method is overloaded you should include the arguments in the
	 * test name like so: testMethodClassInt (for method(Class, int);
	 */


	/**
	 * Test method for {@link org.sakaiproject.evaluation.logic.impl.EvalAssignsLogicImpl#saveAssignGroup(org.sakaiproject.evaluation.model.EvalAssignGroup, java.lang.String)}.
	 */
	public void testSaveAssignContext() {

		// test adding context to inqueue eval
		EvalAssignGroup eacNew = new EvalAssignGroup(new Date(), 
				EvalTestDataLoad.MAINT_USER_ID, EvalTestDataLoad.CONTEXT1, 
				EvalConstants.GROUP_TYPE_SITE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, 
				etdl.evaluationNew);
		assigns.saveAssignGroup(eacNew, EvalTestDataLoad.MAINT_USER_ID);

		// check save worked
		List l = evaluationDao.findByProperties(EvalAssignGroup.class, 
				new String[] {"evaluation.id"}, new Object[] {etdl.evaluationNew.getId()});
		Assert.assertNotNull(l);
		Assert.assertEquals(1, l.size());
		Assert.assertTrue(l.contains(eacNew));

		// test adding context to active eval
		EvalAssignGroup eacActive = new EvalAssignGroup(new Date(), 
				EvalConstants.GROUP_TYPE_SITE, EvalTestDataLoad.MAINT_USER_ID, EvalTestDataLoad.CONTEXT2, 
				Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, 
				etdl.evaluationActive);
		assigns.saveAssignGroup(eacActive, EvalTestDataLoad.MAINT_USER_ID);

		// check save worked
		l = evaluationDao.findByProperties(EvalAssignGroup.class, 
				new String[] {"evaluation.id"}, new Object[] {etdl.evaluationActive.getId()});
		Assert.assertNotNull(l);
		Assert.assertEquals(2, l.size());
		Assert.assertTrue(l.contains(eacActive));

		// test modify safe part while active
		EvalAssignGroup testEac1 = (EvalAssignGroup) evaluationDao.
			findById( EvalAssignGroup.class, etdl.assign1.getId() );
		testEac1.setStudentsViewResults( Boolean.TRUE );
		assigns.saveAssignGroup(testEac1, EvalTestDataLoad.MAINT_USER_ID);

		// test modify safe part while closed
		EvalAssignGroup testEac2 = (EvalAssignGroup) evaluationDao.
			findById( EvalAssignGroup.class, etdl.assign4.getId() );
		testEac2.setStudentsViewResults( Boolean.TRUE );
		assigns.saveAssignGroup(testEac2, EvalTestDataLoad.MAINT_USER_ID);

		// test admin can modify un-owned context
		EvalAssignGroup testEac3 = (EvalAssignGroup) evaluationDao.
			findById( EvalAssignGroup.class, etdl.assign6.getId() );
		testEac3.setStudentsViewResults( Boolean.TRUE );
		assigns.saveAssignGroup(testEac3, EvalTestDataLoad.ADMIN_USER_ID);


		// test cannot add duplicate context to in-queue eval
		try {
			assigns.saveAssignGroup( new EvalAssignGroup(new Date(), 
					EvalTestDataLoad.MAINT_USER_ID, EvalTestDataLoad.CONTEXT1, 
					EvalConstants.GROUP_TYPE_SITE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, 
					etdl.evaluationNew),
					EvalTestDataLoad.MAINT_USER_ID);
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}

		// test cannot add duplicate context to active eval
		try {
			assigns.saveAssignGroup( new EvalAssignGroup(new Date(), 
					EvalTestDataLoad.MAINT_USER_ID, EvalTestDataLoad.CONTEXT1, 
					EvalConstants.GROUP_TYPE_SITE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, 
					etdl.evaluationActive),
					EvalTestDataLoad.MAINT_USER_ID);
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}

		// test user without perm cannot add context to eval
		try {
			assigns.saveAssignGroup( new EvalAssignGroup(new Date(), 
					EvalTestDataLoad.USER_ID, EvalTestDataLoad.CONTEXT1, 
					EvalConstants.GROUP_TYPE_SITE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, 
					etdl.evaluationNew), 
					EvalTestDataLoad.USER_ID);
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}

		// test cannot add context to closed eval
		try {
			assigns.saveAssignGroup( new EvalAssignGroup(new Date(), 
					EvalTestDataLoad.MAINT_USER_ID, EvalTestDataLoad.CONTEXT1, 
					EvalConstants.GROUP_TYPE_SITE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, 
					etdl.evaluationViewable), 
					EvalTestDataLoad.MAINT_USER_ID);
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}

		// test cannot modify non-owned context
		try {
			etdl.assign7.setStudentsViewResults( Boolean.TRUE );
			assigns.saveAssignGroup(etdl.assign7, EvalTestDataLoad.MAINT_USER_ID);
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}


		// TODO - these tests cannot pass right now because of hibernate screwing us -AZ
//		// test modify context while new eval
//		try {
//			EvalAssignContext testEac4 = (EvalAssignContext) evaluationDao.
//				findById( EvalAssignContext.class, etdl.assign6.getId() );
//			testEac4.setContext( EvalTestDataLoad.CONTEXT3 );
//			assigns.saveAssignContext(testEac4, EvalTestDataLoad.MAINT_USER_ID);
//			Assert.fail("Should have thrown exception");
//		} catch (RuntimeException e) {
//			Assert.assertNotNull(e);
//		}
//		
//		// test modify context while active eval
//		try {
//			EvalAssignContext testEac5 = (EvalAssignContext) evaluationDao.
//				findById( EvalAssignContext.class, etdl.assign1.getId() );
//			testEac5.setContext( EvalTestDataLoad.CONTEXT2 );
//			assigns.saveAssignContext(testEac5, EvalTestDataLoad.MAINT_USER_ID);
//			Assert.fail("Should have thrown exception");
//		} catch (RuntimeException e) {
//			Assert.assertNotNull(e);
//		}
//
//		// test modify context while closed eval
//		try {
//			EvalAssignContext testEac6 = (EvalAssignContext) evaluationDao.
//				findById( EvalAssignContext.class, etdl.assign4.getId() );
//			testEac6.setContext( EvalTestDataLoad.CONTEXT1 );
//			assigns.saveAssignContext(testEac6, EvalTestDataLoad.MAINT_USER_ID);
//			Assert.fail("Should have thrown exception");
//		} catch (RuntimeException e) {
//			Assert.assertNotNull(e);
//		}

		// TODO - test that evaluation cannot be changed
		
	}

	/**
	 * Test method for {@link org.sakaiproject.evaluation.logic.impl.EvalAssignsLogicImpl#deleteAssignGroup(java.lang.Long, java.lang.String)}.
	 */
	public void testDeleteAssignContext() {
		// save some ACs to test removing
		EvalAssignGroup eac1 = new EvalAssignGroup(new Date(), 
				EvalTestDataLoad.MAINT_USER_ID, EvalTestDataLoad.CONTEXT1, 
				EvalConstants.GROUP_TYPE_SITE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, 
				etdl.evaluationNew);
		EvalAssignGroup eac2 = new EvalAssignGroup(new Date(), 
				EvalTestDataLoad.ADMIN_USER_ID, EvalTestDataLoad.CONTEXT2, 
				EvalConstants.GROUP_TYPE_SITE, Boolean.FALSE, Boolean.TRUE, Boolean.FALSE, 
				etdl.evaluationNew);
		evaluationDao.save(eac1);
		evaluationDao.save(eac2);

		// check save worked
		List l = evaluationDao.findByProperties(EvalAssignGroup.class, 
				new String[] {"evaluation.id"}, new Object[] {etdl.evaluationNew.getId()});
		Assert.assertNotNull(l);
		Assert.assertEquals(2, l.size());
		Assert.assertTrue(l.contains(eac1));
		Assert.assertTrue(l.contains(eac2));

		// test can remove contexts from new Evaluation
		Long eacId = eac1.getId();
		assigns.deleteAssignGroup( eacId, EvalTestDataLoad.MAINT_USER_ID );

		assigns.deleteAssignGroup( etdl.assign6.getId(), EvalTestDataLoad.MAINT_USER_ID );

		// check save worked
		l = evaluationDao.findByProperties(EvalAssignGroup.class, 
				new String[] {"evaluation.id"}, new Object[] {etdl.evaluationNew.getId()});
		Assert.assertNotNull(l);
		Assert.assertEquals(1, l.size());
		Assert.assertTrue(! l.contains(eac1));

		// test cannot remove context from active eval
		try {
			assigns.deleteAssignGroup( etdl.assign1.getId(), 
					EvalTestDataLoad.MAINT_USER_ID );
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}

		// test cannot remove context from closed eval
		try {
			assigns.deleteAssignGroup( etdl.assign4.getId(), 
					EvalTestDataLoad.MAINT_USER_ID );
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}

		// test cannot remove context without permission
		try {
			assigns.deleteAssignGroup( eac2.getId(), 
					EvalTestDataLoad.USER_ID );
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}

		// test cannot remove context without ownership
		try {
			assigns.deleteAssignGroup( etdl.assign7.getId(), 
					EvalTestDataLoad.MAINT_USER_ID );
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}

		// test remove invalid eac
		try {
			assigns.deleteAssignGroup( EvalTestDataLoad.INVALID_LONG_ID, 
					EvalTestDataLoad.MAINT_USER_ID );
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}

	}

	/**
	 * Test method for {@link org.sakaiproject.evaluation.logic.impl.EvalAssignsLogicImpl#getAssignGroupsByEvalId(java.lang.Long)}.
	 */
	public void testGetAssignContextsByEvalId() {
		List l = null;
		List ids = null;

		// test fetch ACs from closed
		l = assigns.getAssignGroupsByEvalId( etdl.evaluationClosed.getId() );
		Assert.assertNotNull(l);
		Assert.assertEquals(2, l.size());
		ids = EvalTestDataLoad.makeIdList(l);
		Assert.assertTrue(ids.contains( etdl.assign3.getId() ));
		Assert.assertTrue(ids.contains( etdl.assign4.getId() ));

		// test fetch ACs from active
		l = assigns.getAssignGroupsByEvalId( etdl.evaluationActive.getId() );
		Assert.assertNotNull(l);
		Assert.assertEquals(1, l.size());
		ids = EvalTestDataLoad.makeIdList(l);
		Assert.assertTrue(ids.contains( etdl.assign1.getId() ));

		// test fetch ACs from new
		l = assigns.getAssignGroupsByEvalId( etdl.evaluationNew.getId() );
		Assert.assertNotNull(l);
		Assert.assertEquals(0, l.size());

		// test fetch from invalid id
		try {
			l = assigns.getAssignGroupsByEvalId( EvalTestDataLoad.INVALID_LONG_ID );
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}
	}


	/**
	 * Test method for {@link org.sakaiproject.evaluation.logic.impl.EvalAssignsLogicImpl#canCreateAssignEval(java.lang.String, java.lang.Long)}.
	 */
	public void testCanCreateAssignEval() {
		// test can create an AC in new
		Assert.assertTrue( assigns.canCreateAssignEval(
				EvalTestDataLoad.MAINT_USER_ID,	etdl.evaluationNew.getId()) );
		Assert.assertTrue( assigns.canCreateAssignEval(
				EvalTestDataLoad.ADMIN_USER_ID,	etdl.evaluationNew.getId()) );
		Assert.assertTrue( assigns.canCreateAssignEval(
				EvalTestDataLoad.ADMIN_USER_ID,	etdl.evaluationNewAdmin.getId()) );
		Assert.assertTrue( assigns.canCreateAssignEval(
				EvalTestDataLoad.MAINT_USER_ID,	etdl.evaluationActive.getId()) );
		Assert.assertTrue( assigns.canCreateAssignEval(
				EvalTestDataLoad.ADMIN_USER_ID,	etdl.evaluationActive.getId()) );

		// test cannot create AC in closed evals
		Assert.assertFalse( assigns.canCreateAssignEval(
				EvalTestDataLoad.MAINT_USER_ID,	etdl.evaluationClosed.getId()) );
		Assert.assertFalse( assigns.canCreateAssignEval(
				EvalTestDataLoad.MAINT_USER_ID,	etdl.evaluationViewable.getId()) );

		// test cannot create AC without perms
		Assert.assertFalse( assigns.canCreateAssignEval(
				EvalTestDataLoad.USER_ID, etdl.evaluationNew.getId()) );
		Assert.assertFalse( assigns.canCreateAssignEval(
				EvalTestDataLoad.MAINT_USER_ID,	etdl.evaluationNewAdmin.getId()) );

		// test invalid evaluation id
		try {
			assigns.canCreateAssignEval(
					EvalTestDataLoad.MAINT_USER_ID,	EvalTestDataLoad.INVALID_LONG_ID );
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}
	}

	/**
	 * Test method for {@link org.sakaiproject.evaluation.logic.impl.EvalAssignsLogicImpl#canDeleteAssignGroup(java.lang.String, java.lang.Long)}.
	 */
	public void testCanDeleteAssignContext() {
		// test can remove an AC in new eval
		Assert.assertTrue( assigns.canDeleteAssignGroup(
				EvalTestDataLoad.MAINT_USER_ID,	etdl.assign6.getId()) );
		Assert.assertTrue( assigns.canDeleteAssignGroup(
				EvalTestDataLoad.ADMIN_USER_ID,	etdl.assign6.getId()) );
		Assert.assertTrue( assigns.canDeleteAssignGroup(
				EvalTestDataLoad.ADMIN_USER_ID,	etdl.assign7.getId()) );

		// test cannot remove AC from running evals
		Assert.assertFalse( assigns.canDeleteAssignGroup(
				EvalTestDataLoad.MAINT_USER_ID, etdl.assign1.getId()) );
		Assert.assertFalse( assigns.canDeleteAssignGroup(
				EvalTestDataLoad.MAINT_USER_ID, etdl.assign4.getId()) );
		Assert.assertFalse( assigns.canDeleteAssignGroup(
				EvalTestDataLoad.ADMIN_USER_ID, etdl.assign3.getId()) );
		Assert.assertFalse( assigns.canDeleteAssignGroup(
				EvalTestDataLoad.ADMIN_USER_ID, etdl.assign5.getId()) );

		// test cannot remove without permission
		Assert.assertFalse( assigns.canDeleteAssignGroup(
				EvalTestDataLoad.USER_ID, etdl.assign6.getId()) );
		Assert.assertFalse( assigns.canDeleteAssignGroup(
				EvalTestDataLoad.MAINT_USER_ID, etdl.assign7.getId()) );

		// test invalid evaluation id
		try {
			assigns.canDeleteAssignGroup(
					EvalTestDataLoad.MAINT_USER_ID,	EvalTestDataLoad.INVALID_LONG_ID );
			Assert.fail("Should have thrown exception");
		} catch (RuntimeException e) {
			Assert.assertNotNull(e);
		}

	}

}
