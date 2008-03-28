/**
 * $Id$
 * $URL$
 * TemplateItemDataListTest.java - evaluation - Mar 28, 2008 9:52:05 AM - azeckoski
 **************************************************************************
 * Copyright (c) 2008 Centre for Applied Research in Educational Technologies, University of Cambridge
 * Licensed under the Educational Community License version 1.0
 * 
 * A copy of the Educational Community License has been included in this 
 * distribution and is available at: http://www.opensource.org/licenses/ecl1.php
 *
 * Aaron Zeckoski (azeckoski@gmail.com) (aaronz@vt.edu) (aaron@caret.cam.ac.uk)
 */

package org.sakaiproject.evaluation.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.sakaiproject.evaluation.constant.EvalConstants;
import org.sakaiproject.evaluation.logic.model.EvalHierarchyNode;
import org.sakaiproject.evaluation.model.EvalTemplateItem;
import org.sakaiproject.evaluation.test.EvalTestDataLoad;

import junit.framework.TestCase;


/**
 * Tests the template items data structure to make sure everything is working
 * 
 * @author Aaron Zeckoski (aaron@caret.cam.ac.uk)
 */
public class TemplateItemDataListTest extends TestCase {

   /**
    * Test method for {@link org.sakaiproject.evaluation.utils.TemplateItemDataList#TemplateItemDataList(java.util.List, java.util.List, java.util.Map)}.
    */
   public void testTemplateItemDataList() {
      EvalTestDataLoad etdl = new EvalTestDataLoad();

      List<EvalTemplateItem> testList = new ArrayList<EvalTemplateItem>();
      TemplateItemDataList tidl = null;

      // test empty TI list fails
      try {
         tidl = new TemplateItemDataList(testList, null, null);
         fail("Should have thrown exception");
      } catch (IllegalArgumentException e) {
         assertNotNull(e.getMessage());
      }

      // test that we can produce a valid one
      testList.add(etdl.templateItem2A); // course
      testList.add(etdl.templateItem3A); // course
      testList.add(etdl.templateItem5A); // instructor

      tidl = new TemplateItemDataList(testList, null, null);
      assertNotNull(tidl);
      assertEquals(3, tidl.getTemplateItemsCount());
      assertEquals(3, tidl.getNonChildItemsCount());
      assertEquals(1, tidl.getAssociateGroupingsCount());
      assertEquals(EvalConstants.ITEM_CATEGORY_COURSE, tidl.get(0).associateType);

      // now add in some associates
      Map<String, List<String>> associates = new HashMap<String, List<String>>();
      List<String> associateIds = new ArrayList<String>();
      associateIds.add(EvalTestDataLoad.MAINT_USER_ID);
      associates.put(EvalConstants.ITEM_CATEGORY_INSTRUCTOR, associateIds);

      tidl = new TemplateItemDataList(testList, null, associates);
      assertNotNull(tidl);
      assertEquals(3, tidl.getTemplateItemsCount());
      assertEquals(3, tidl.getNonChildItemsCount());
      assertEquals(2, tidl.getAssociateGroupingsCount());
      assertEquals(EvalConstants.ITEM_CATEGORY_COURSE, tidl.get(0).associateType);
      assertEquals(2, tidl.get(0).getTemplateItemsCount());
      assertEquals(EvalConstants.ITEM_CATEGORY_INSTRUCTOR, tidl.get(1).associateType);
      assertEquals(1, tidl.get(1).getTemplateItemsCount());
      assertEquals(EvalTestDataLoad.MAINT_USER_ID, tidl.get(1).associateId);
      assertEquals(2, tidl.getAssociateTypes().size());
      assertEquals(EvalConstants.ITEM_CATEGORY_COURSE, tidl.getAssociateTypes().get(0));
      assertEquals(EvalConstants.ITEM_CATEGORY_INSTRUCTOR, tidl.getAssociateTypes().get(1));

      // now test multiple associates
      associateIds.add(EvalTestDataLoad.ADMIN_USER_ID);
      associates.put(EvalConstants.ITEM_CATEGORY_INSTRUCTOR, associateIds);

      tidl = new TemplateItemDataList(testList, null, associates);
      assertNotNull(tidl);
      assertEquals(3, tidl.getTemplateItemsCount());
      assertEquals(3, tidl.getNonChildItemsCount());
      assertEquals(3, tidl.getAssociateGroupingsCount());
      assertEquals(EvalConstants.ITEM_CATEGORY_COURSE, tidl.get(0).associateType);
      assertEquals(2, tidl.get(0).getTemplateItemsCount());
      assertEquals(EvalConstants.ITEM_CATEGORY_INSTRUCTOR, tidl.get(1).associateType);
      assertEquals(1, tidl.get(1).getTemplateItemsCount());
      assertEquals(EvalConstants.ITEM_CATEGORY_INSTRUCTOR, tidl.get(2).associateType);
      assertEquals(1, tidl.get(2).getTemplateItemsCount());
      assertEquals(2, tidl.getAssociateTypes().size());
      assertEquals(EvalConstants.ITEM_CATEGORY_COURSE, tidl.getAssociateTypes().get(0));
      assertEquals(EvalConstants.ITEM_CATEGORY_INSTRUCTOR, tidl.getAssociateTypes().get(1));

      // now test adding in some hierarchy nodes
      List<EvalHierarchyNode> nodes = new ArrayList<EvalHierarchyNode>();
      nodes.add( new EvalHierarchyNode("node1", "node title", "description") );

      associateIds.clear();
      associateIds.add(EvalTestDataLoad.MAINT_USER_ID);
      associates.put(EvalConstants.ITEM_CATEGORY_INSTRUCTOR, associateIds);

      tidl = new TemplateItemDataList(testList, nodes, associates);
      assertNotNull(tidl);
      assertEquals(3, tidl.getTemplateItemsCount());
      assertEquals(3, tidl.getNonChildItemsCount());
      assertEquals(2, tidl.getAssociateGroupingsCount());
      assertEquals(EvalConstants.ITEM_CATEGORY_COURSE, tidl.get(0).associateType);
      assertEquals(2, tidl.get(0).getTemplateItemsCount());
      assertEquals(EvalConstants.ITEM_CATEGORY_INSTRUCTOR, tidl.get(1).associateType);
      assertEquals(1, tidl.get(1).getTemplateItemsCount());
      assertEquals(2, tidl.getAssociateTypes().size());
      assertEquals(EvalConstants.ITEM_CATEGORY_COURSE, tidl.getAssociateTypes().get(0));
      assertEquals(EvalConstants.ITEM_CATEGORY_INSTRUCTOR, tidl.getAssociateTypes().get(1));
      // node1 is not used so we will only get the top level nodes back
      assertEquals(1, tidl.get(0).hierarchyNodeGroups.size());
      assertNull(tidl.get(0).hierarchyNodeGroups.get(0).node);
      assertEquals(1, tidl.get(1).hierarchyNodeGroups.size());
      assertNull(tidl.get(1).hierarchyNodeGroups.get(0).node);

      // TODO add in test data for TIs associated with nodes at some point
   }

}