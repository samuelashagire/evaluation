/**
 * $Id: EvalExternalLogicImpl.java 1000 Dec 24, 2006 10:07:31 AM azeckoski $
 * $URL: https://source.sakaiproject.org/contrib $
 * EvalExternalLogicImpl.java - evaluation - Dec 24, 2006 10:07:31 AM - azeckoski
 **************************************************************************
 * Copyright (c) 2008 Centre for Applied Research in Educational Technologies, University of Cambridge
 * Licensed under the Educational Community License version 1.0
 * 
 * A copy of the Educational Community License has been included in this 
 * distribution and is available at: http://www.opensource.org/licenses/ecl1.php
 *
 * Aaron Zeckoski (azeckoski@gmail.com) (aaronz@vt.edu) (aaron@caret.cam.ac.uk)
 */

package org.sakaiproject.evaluation.logic.impl;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Set;

import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.sakaiproject.authz.api.AuthzGroupService;
import org.sakaiproject.authz.api.FunctionManager;
import org.sakaiproject.authz.api.SecurityService;
import org.sakaiproject.component.api.ServerConfigurationService;
import org.sakaiproject.email.api.EmailService;
import org.sakaiproject.entity.api.EntityManager;
import org.sakaiproject.entity.api.Reference;
import org.sakaiproject.entitybroker.EntityBroker;
import org.sakaiproject.entitybroker.IdEntityReference;
import org.sakaiproject.evaluation.logic.EvalExternalLogic;
import org.sakaiproject.evaluation.logic.entity.AssignGroupEntityProvider;
import org.sakaiproject.evaluation.logic.entity.EvaluationEntityProvider;
import org.sakaiproject.evaluation.logic.entity.ItemEntityProvider;
import org.sakaiproject.evaluation.logic.entity.TemplateEntityProvider;
import org.sakaiproject.evaluation.logic.entity.TemplateItemEntityProvider;
import org.sakaiproject.evaluation.logic.model.EvalGroup;
import org.sakaiproject.evaluation.logic.providers.EvalGroupsProvider;
import org.sakaiproject.evaluation.logic.utils.ArrayUtils;
import org.sakaiproject.evaluation.model.EvalAssignGroup;
import org.sakaiproject.evaluation.model.EvalAssignHierarchy;
import org.sakaiproject.evaluation.model.EvalEvaluation;
import org.sakaiproject.evaluation.model.EvalItem;
import org.sakaiproject.evaluation.model.EvalResponse;
import org.sakaiproject.evaluation.model.EvalScale;
import org.sakaiproject.evaluation.model.EvalTemplate;
import org.sakaiproject.evaluation.model.EvalTemplateItem;
import org.sakaiproject.evaluation.model.constant.EvalConstants;
import org.sakaiproject.exception.IdUnusedException;
import org.sakaiproject.site.api.Group;
import org.sakaiproject.site.api.Site;
import org.sakaiproject.site.api.SiteService;
import org.sakaiproject.tool.api.Session;
import org.sakaiproject.tool.api.SessionManager;
import org.sakaiproject.tool.api.ToolManager;
import org.sakaiproject.user.api.User;
import org.sakaiproject.user.api.UserDirectoryService;
import org.sakaiproject.user.api.UserNotDefinedException;
import org.sakaiproject.util.ResourceLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;


/**
 * This class handles the Sakai based implementation of the external logic inteface<br/>
 * This is sort of the provider for the evaluation system though it should be broken up
 * if it is going to be used like that,
 * This is a BOTTOM level service and should depend on no other eval services (only those in Sakai)
 *
 * @author Aaron Zeckoski (aaronz@vt.edu)
 */
public class EvalExternalLogicImpl implements EvalExternalLogic, ApplicationContextAware {

   private static Log log = LogFactory.getLog(EvalExternalLogicImpl.class);

   private static final String SAKAI_SITE_TYPE = SiteService.SITE_SUBTYPE;
   private static final String SAKAI_GROUP_TYPE = SiteService.GROUP_SUBTYPE;

   private static final String ANON_USER_ATTRIBUTE = "AnonUserAttribute";
   private static final String ANON_USER_PREFIX = "Anon_User_";

   private AuthzGroupService authzGroupService;
   public void setAuthzGroupService(AuthzGroupService authzGroupService) {
      this.authzGroupService = authzGroupService;
   }

   private EmailService emailService;
   public void setEmailService(EmailService emailService) {
      this.emailService = emailService;
   }

   private EntityBroker entityBroker;
   public void setEntityBroker(EntityBroker entityBroker) {
      this.entityBroker = entityBroker;
   }

   private EntityManager entityManager;
   public void setEntityManager(EntityManager entityManager) {
      this.entityManager = entityManager;
   }

   private FunctionManager functionManager;
   public void setFunctionManager(FunctionManager functionManager) {
      this.functionManager = functionManager;
   }

   private SecurityService securityService;
   public void setSecurityService(SecurityService securityService) {
      this.securityService = securityService;
   }

   private ServerConfigurationService serverConfigurationService;
   public void setServerConfigurationService(ServerConfigurationService serverConfigurationService) {
      this.serverConfigurationService = serverConfigurationService;
   }

   private SessionManager sessionManager;
   public void setSessionManager(SessionManager sessionManager) {
      this.sessionManager = sessionManager;
   }

   private SiteService siteService;
   public void setSiteService(SiteService siteService) {
      this.siteService = siteService;
   }

   private ToolManager toolManager;
   public void setToolManager(ToolManager toolManager) {
      this.toolManager = toolManager;
   }

   private UserDirectoryService userDirectoryService;
   public void setUserDirectoryService(UserDirectoryService userDirectoryService) {
      this.userDirectoryService = userDirectoryService;
   }

   private ApplicationContext applicationContext;
   public void setApplicationContext(ApplicationContext applicationContext) {
      this.applicationContext = applicationContext;
   }


   // PROVIDERS

   private EvalGroupsProvider evalGroupsProvider;
   public void setEvalGroupsProvider(EvalGroupsProvider evalGroupsProvider) {
      this.evalGroupsProvider = evalGroupsProvider;
   }


   public void init() {
      log.debug("init, register security perms");

      // register Sakai permissions for this tool
      registerPermissions();

      // setup provider
      if (evalGroupsProvider == null) {
         String providerBeanName = EvalGroupsProvider.class.getName();
         if (applicationContext.containsBean(providerBeanName)) {
            evalGroupsProvider = (EvalGroupsProvider) applicationContext.getBean(providerBeanName);
            log.info("EvalGroupsProvider found...");
         } else {
            log.debug("No EvalGroupsProvider found...");
         }
      }
   }


   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.external.EvalExternalLogic#getCurrentUserId()
    */
   public String getCurrentUserId() {
      String userId = sessionManager.getCurrentSessionUserId();
      if (userId == null) {
         // if no user found then fake like there is one for this session,
         // we do not want to actually create a user though
         Session session = sessionManager.getCurrentSession();
         userId = (String) session.getAttribute(ANON_USER_ATTRIBUTE);
         if (userId == null) {
            String sessionUserId = session.getId() + new Date().getTime();
            userId = ANON_USER_PREFIX + makeMD5(sessionUserId, 40);
            session.setAttribute(ANON_USER_ATTRIBUTE, userId);
         }
      }
      return userId;
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.externals.ExternalUsers#isUserAnonymous(java.lang.String)
    */
   public boolean isUserAnonymous(String userId) {
      String currentUserId = sessionManager.getCurrentSessionUserId();
      if (userId.equals(currentUserId)) {
         return false;
      }
      Session session = sessionManager.getCurrentSession();
      String sessionUserId = (String) session.getAttribute(ANON_USER_ATTRIBUTE);
      if (userId.equals(sessionUserId)) {
         return true;
      }
      // used up both cheap tests, now try the costly one
      try {
         // try to lookup this user, exception if we cannot find them
         userDirectoryService.getUserEid(userId);
         return true;
      } catch (Exception e) {
         return false;
      }
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.external.EvalExternalLogic#getUserUsername(java.lang.String)
    */
   public String getUserUsername(String userId) {
      if (isUserAnonymous(userId)) {
         return "anonymous";
      }
      try {
         return userDirectoryService.getUserEid(userId);
      } catch(UserNotDefinedException ex) {
         log.error("Could not get username from userId: " + userId, ex);
      }
      return "------";
   }



   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.externals.ExternalUsers#getUserId(java.lang.String)
    */
   public String getUserId(String username) {
      try {
         return userDirectoryService.getUserId(username);
      } catch(UserNotDefinedException ex) {
         log.error("Could not get userId from username: " + username);
      }
      return null;
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.external.EvalExternalLogic#getUserDisplayName(java.lang.String)
    */
   public String getUserDisplayName(String userId) {
      if (isUserAnonymous(userId)) {
         return "Anonymous User";
      }
      try {
         User user = userDirectoryService.getUser(userId);
         return user.getDisplayName();
      } catch(UserNotDefinedException ex) {
         log.error("Could not get user from userId: " + userId, ex);
      }
      return "----------";
   }


   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.external.EvalExternalLogic#isUserAdmin(java.lang.String)
    */
   public boolean isUserAdmin(String userId) {
      log.debug("Checking is eval super admin for: " + userId);
      return securityService.isSuperUser(userId);
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.externals.ExternalUsers#getUserLocale(java.lang.String)
    */
   public Locale getUserLocale(String userId) {
      log.debug("userId: " + userId);
      log.warn("can only get the locale for the current user right now...");
      // TODO - this sucks because there is no way to get the locale for anything but the
      // current user.... terrible -AZ
      return new ResourceLoader().getLocale();
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.externals.ExternalEvalGroups#makeEvalGroupObject(java.lang.String)
    */
   public EvalGroup makeEvalGroupObject(String evalGroupId) {
      EvalGroup c = null;
      try {
         // try to get the site object based on the entity reference (which is the evalGroupId)
         Object entity = entityBroker.fetchEntity(evalGroupId);
         if (entity instanceof Site) {
            Site site = (Site) entityBroker.fetchEntity(evalGroupId);
            c = new EvalGroup( evalGroupId, site.getTitle(), 
                  getContextType(SAKAI_SITE_TYPE) );
         } else if (entity instanceof Group) {
            Group group = (Group) entityBroker.fetchEntity(evalGroupId);
            c = new EvalGroup( evalGroupId, group.getTitle(), 
                  getContextType(SAKAI_GROUP_TYPE) );
         }
      } catch (Exception e) {
         // invalid site reference
         log.debug("Could not get sakai site from evalGroupId:" + evalGroupId, e);
         c = null;
      }

      if (c == null) {
         // use external provider
         if (evalGroupsProvider != null) {
            c = evalGroupsProvider.getGroupByGroupId(evalGroupId);
            if (c != null) {
               c.type = EvalConstants.GROUP_TYPE_PROVIDED;
            }
         }
      }

      if (c == null) {
         log.error("Could not get group from evalGroupId:" + evalGroupId);
         // create a fake group placeholder as an error notice
         c = new EvalGroup( evalGroupId, "** INVALID: "+evalGroupId+" **", 
               EvalConstants.GROUP_TYPE_INVALID );
      }

      return c;
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.externals.ExternalEvalGroups#getCurrentEvalGroup()
    */
   public String getCurrentEvalGroup() {
      String location = null;
      try {
         String context = toolManager.getCurrentPlacement().getContext();
         try {
            Site s = siteService.getSite( context );
            location = s.getReference(); // get the entity reference to the site
         } catch (IdUnusedException e1) {
           log.debug("Failed to get current site, trying to find current group"); 
           Group group = siteService.findGroup( context );
           if ( group != null ) {
              location = group.getReference();
           }
         }
      } catch (Exception e) {
         log.warn("Failure while attempting to get current location: " + e.getMessage());
         location = null;
      }

      if (location == null) {
         log.warn("Could not get the current location (we are probably outside the portal), returning the fake one");
         location = NO_LOCATION;
      }
      return location;
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.external.EvalExternalLogic#getDisplayTitle(java.lang.String)
    */
   public String getDisplayTitle(String evalGroupId) {
      EvalGroup group = makeEvalGroupObject(evalGroupId);
      if (group != null && group.title != null) {
         return group.title;
      }
      log.warn("Cannot get the title for evalGroupId: " + evalGroupId);
      return "--------";
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.externals.ExternalEvalGroups#countEvalGroupsForUser(java.lang.String, java.lang.String)
    */
   @SuppressWarnings("unchecked")
   public int countEvalGroupsForUser(String userId, String permission) {
      log.debug("userId: " + userId + ", permission: " + permission);

      int count = 0;
      Set<String> authzGroupIds = authzGroupService.getAuthzGroupsIsAllowed(userId, permission, null);
      Iterator<String> it = authzGroupIds.iterator();
      while (it.hasNext()) {
         String authzGroupId = it.next();
         Reference r = entityManager.newReference(authzGroupId);
         if(r.isKnownType()) {
            // check if this is a Sakai Site or Group
            if(r.getType().equals(SiteService.APPLICATION_ID)) {
               count++;
            }
         }
      }

      // also check provider
      if (evalGroupsProvider != null) {
         if (EvalConstants.PERM_BE_EVALUATED.equals(permission) ||
               EvalConstants.PERM_TAKE_EVALUATION.equals(permission) ) {
            log.debug("Using eval groups provider: userId: " + userId + ", permission: " + permission);
            count += evalGroupsProvider.countEvalGroupsForUser(userId, translatePermission(permission));
         }
      }

      return count;
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.externals.ExternalEvalGroups#getEvalGroupsForUser(java.lang.String, java.lang.String)
    */
   @SuppressWarnings("unchecked")
   public List<EvalGroup> getEvalGroupsForUser(String userId, String permission) {
      log.debug("userId: " + userId + ", permission: " + permission);

      List<EvalGroup> l = new ArrayList<EvalGroup>();
      Set<String> authzGroupIds = 
         authzGroupService.getAuthzGroupsIsAllowed(userId, permission, null);
      Iterator<String> it = authzGroupIds.iterator();
      while (it.hasNext()) {
         String authzGroupId = it.next();
         Reference r = entityManager.newReference(authzGroupId);
         if (r.isKnownType()) {
            // check if this is a Sakai Site or Group
            if (r.getType().equals(SiteService.APPLICATION_ID)) {
               String type = r.getSubType();
               if (SAKAI_SITE_TYPE.equals(type)) {
                  // this is a Site
                  String siteId = r.getId();
                  try {
                     Site site = siteService.getSite(siteId);
                     l.add(new EvalGroup(r.getReference(), site.getTitle(), 
                           getContextType(r.getType())));
                  } catch (IdUnusedException e) {
                     // invalid site Id returned
                     throw new RuntimeException("Could not get site from siteId:" + siteId);
                  }

               } else if (SAKAI_GROUP_TYPE.equals(type)) {
                  // this is a Group in a Site
                  String groupId = r.getId();
                  Group group = siteService.findGroup(groupId);
                  if (group == null) {
                     throw new RuntimeException("Could not get group from group id:" + groupId);
                  }
                  l.add(new EvalGroup(r.getReference(), group.getTitle(), 
                        getContextType(r.getType())));
               }
            }
         }
      }

      // also check provider
      if (evalGroupsProvider != null) {
         if (EvalConstants.PERM_BE_EVALUATED.equals(permission) ||
               EvalConstants.PERM_TAKE_EVALUATION.equals(permission) ) {
            log.debug("Using eval groups provider: userId: " + userId + ", permission: " + permission);
            List eg = evalGroupsProvider.getEvalGroupsForUser(userId, translatePermission(permission));
            for (Iterator iter = eg.iterator(); iter.hasNext();) {
               EvalGroup c = (EvalGroup) iter.next();
               c.type = EvalConstants.GROUP_TYPE_PROVIDED;
               l.add(c);
            }
         }
      }

      if (l.isEmpty()) log.warn("Empty list of groups for user:" + userId + ", permission: " + permission);
      return l;
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.externals.ExternalEvalGroups#countUserIdsForEvalGroup(java.lang.String, java.lang.String)
    */
   public int countUserIdsForEvalGroup(String evalGroupId, String permission) {
      int count = getUserIdsForEvalGroup(evalGroupId, permission).size();

      // also check provider
      if (evalGroupsProvider != null) {
         if (EvalConstants.PERM_BE_EVALUATED.equals(permission) ||
               EvalConstants.PERM_TAKE_EVALUATION.equals(permission) ) {
            log.debug("Using eval groups provider: evalGroupId: " + evalGroupId + ", permission: " + permission);
            count += evalGroupsProvider.countUserIdsForEvalGroups(new String[] {evalGroupId}, translatePermission(permission));
         }
      }

      return count;
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.externals.ExternalEvalGroups#getUserIdsForEvalGroup(java.lang.String, java.lang.String)
    */
   @SuppressWarnings("unchecked")
   public Set<String> getUserIdsForEvalGroup(String evalGroupId, String permission) {
      String reference = evalGroupId;
      List<String> azGroups = new ArrayList<String>();
      azGroups.add(reference);
      Set<String> userIds = authzGroupService.getUsersIsAllowed(permission, azGroups);

      // also check provider
      if (evalGroupsProvider != null) {
         if (EvalConstants.PERM_BE_EVALUATED.equals(permission) ||
               EvalConstants.PERM_TAKE_EVALUATION.equals(permission) ) {
            log.debug("Using eval groups provider: evalGroupId: " + evalGroupId + ", permission: " + permission);
            userIds.addAll( evalGroupsProvider.getUserIdsForEvalGroups(new String[] {evalGroupId}, translatePermission(permission)) );
         }
      }

      return userIds;
   }


   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.EvalExternalLogic#isUserAllowedInEvalGroup(java.lang.String, java.lang.String, java.lang.String)
    */
   public boolean isUserAllowedInEvalGroup(String userId, String permission, String evalGroupId) {
      if (evalGroupId == null) {
         if (securityService.isSuperUser(userId)) {
            return true;
         }
         return false;
      }

      String reference = evalGroupId;
      if ( securityService.unlock(userId, permission, reference) ) {
         return true;
      }

      // also check provider
      if (evalGroupsProvider != null) {
         if (EvalConstants.PERM_BE_EVALUATED.equals(permission) ||
               EvalConstants.PERM_TAKE_EVALUATION.equals(permission) ) {
            log.debug("Using eval groups provider: userId: " + userId + ", permission: " + permission + ", evalGroupId: " + evalGroupId);
            if ( evalGroupsProvider.isUserAllowedInGroup(userId, translatePermission(permission), evalGroupId) ) {
               return true;
            }
         }
      }

      return false;
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.EvalExternalLogic#sendEmails(java.lang.String, java.lang.String[], java.lang.String, java.lang.String)
    */
   public String[] sendEmailsToUsers(String from, String[] toUserIds, String subject, String message, boolean deferExceptions) {
      String exceptionTracker = null;

      InternetAddress fromAddress;
      try {
         fromAddress = new InternetAddress(from);
      } catch (AddressException e) {
         // cannot recover from this failure
         throw new IllegalArgumentException("Invalid from address: " + from, e);
      }

      // handle the list of TO addresses
      // TODO - cannot use this because of the way the UDS works (it will not let us call this unless
      // the user already exists in Sakai -AZ
//    // get the list of users efficiently
//    List userIds = Arrays.asList( toUserIds );
//    List l = userDirectoryService.getUsers( userIds );

      // handling this in a much less efficient way for now (see above comment) -AZ
      List<User> l = new ArrayList<User>(); // fill this with users
      for (int i = 0; i < toUserIds.length; i++) {
         User user;
         try {
            user = userDirectoryService.getUser( toUserIds[i] );
         } catch (UserNotDefinedException e) {
            log.debug("Cannot find user object by id:" + toUserIds[i] );
            try {
               user = userDirectoryService.getUserByEid( toUserIds[i] );
            } catch (UserNotDefinedException e1) {
               if (deferExceptions) {
                  exceptionTracker += e.getMessage() + " :: ";
                  log.error("Deferring exception: Failed to find user: " + toUserIds[i], e);
                  continue;
               } else {
                  // die here since we were unable to find this user at all
                  throw new IllegalArgumentException("Invalid user: Cannot find user object by id or eid:" + toUserIds[i], e );
               } 
            }
         }
         l.add(user);
      }
      // end of much less efficient way of doing things -AZ

      // email address validity is checked at entry but value can be null
      List<String> toEmails = new ArrayList<String>();
      for (ListIterator<User> iterator = l.listIterator(); iterator.hasNext();) {
         User u = iterator.next();
         if ( u.getEmail() == null || "".equals(u.getEmail()) ) {
            iterator.remove();
            log.warn("sendEmails: Could not get an email address for " + u.getDisplayName() + " ("+u.getId()+")");
         } else {
            toEmails.add(u.getEmail());
         }
      }

      if (l == null || l.size() <= 0) {
         log.warn("No users with email addresses found in the provided userIds ("+ArrayUtils.arrayToString(toUserIds)+"), cannot send email so exiting");
         return new String[] {};
      }

      return sendEmails(fromAddress, toEmails, subject, message, deferExceptions, exceptionTracker);
   }

   public String[] sendEmailsToAddresses(String from, String[] to, String subject, String message, boolean deferExceptions) {
      String exceptionTracker = null;

      InternetAddress fromAddress;
      try {
         fromAddress = new InternetAddress(from);
      } catch (AddressException e) {
         // cannot recover from this failure
         throw new IllegalArgumentException("Invalid from address: " + from, e);
      }

      List<String> toEmails = new ArrayList<String>();
      for (int i = 0; i < to.length; i++) {
         String email = to[i];
         if (email == null || email.equals("")) {
            if (deferExceptions) {
               exceptionTracker += "blank or null to address in list ("+i+") :: ";
               log.error("blank or null to address in list ("+i+"): " + ArrayUtils.arrayToString(to));
               continue;
            } else {
               // die here since we were unable to find this user at all
               throw new IllegalArgumentException("blank or null to address ("+i+"): " + ArrayUtils.arrayToString(to) + ", cannot send emails");
            }
         }
         toEmails.add(email);
      }

      return sendEmails(fromAddress, toEmails, subject, message, deferExceptions, exceptionTracker);
   }

   /**
    * Handle the actual sending of the email
    * @param fromAddress
    * @param toEmails
    * @param subject
    * @param message
    * @param deferExceptions
    * @param exceptionTracker
    * @return
    */
   private String[] sendEmails(InternetAddress fromAddress, List<String> toEmails, String subject,
         String message, boolean deferExceptions, String exceptionTracker) {
      InternetAddress[] replyTo = new InternetAddress[1];
      List<InternetAddress> listAddresses = new ArrayList<InternetAddress>();
      for (int i = 0; i < toEmails.size(); i++) {
         String email = toEmails.get(i);
         try {
            InternetAddress toAddress = new InternetAddress(email);
            listAddresses.add(toAddress);
         } catch (AddressException e) {
            if (deferExceptions) {
               exceptionTracker += e.getMessage() + " :: ";
               log.error("Invalid to address: " + email + ", skipping...", e);
               continue;
            } else {
               // die here since we were unable to find this user at all
               throw new IllegalArgumentException("Invalid to address: " + email + ", cannot send emails", e);
            } 
         }
      }
      replyTo[0] = fromAddress;
      InternetAddress[] toAddresses = listAddresses.toArray(new InternetAddress[listAddresses.size()]);
      emailService.sendMail(fromAddress, toAddresses, subject, message, null, replyTo, null);

      if (deferExceptions && exceptionTracker != null) {
         // exceptions occurred so we have to die here
         throw new IllegalArgumentException("The following exceptions were deferred (full trace above in the log): " + 
               "Sent emails to " + toAddresses.length + " addresses before failure ocurred: Failure summary: " +
               exceptionTracker);
      }

      // now we send back the list of people who the email was sent to
      String[] addresses = new String[toAddresses.length];
      for (int i = 0; i < toAddresses.length; i++) {
         addresses[i] = toAddresses[i].getAddress();
      }
      return addresses;
   }

   
   
   // ENTITIES


   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.EvalExternalLogic#getServerUrl()
    */
   public String getServerUrl() {
      return serverConfigurationService.getPortalUrl();
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.EvalExternalLogic#getEntityURL(java.io.Serializable)
    */
   public String getEntityURL(Serializable evaluationEntity) {
      String ref = getEntityReference(evaluationEntity);
      if (ref != null) {
         return entityBroker.getEntityURL(ref);
      } else {
         return serverConfigurationService.getPortalUrl();
      }
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.EvalExternalLogic#getEntityURL(java.lang.String, java.lang.String)
    */
   public String getEntityURL(String entityPrefix, String entityId) {
      String ref = new IdEntityReference(entityPrefix, entityId).toString();
      if (ref != null) {
         return entityBroker.getEntityURL(ref);
      } else {
         return serverConfigurationService.getPortalUrl();
      }
   }

   /* (non-Javadoc)
    * @see org.sakaiproject.evaluation.logic.EvalExternalLogic#registerEntityEvent(java.lang.String, java.io.Serializable)
    */
   public void registerEntityEvent(String eventName, Serializable evaluationEntity) {
      String ref = getEntityReference(evaluationEntity);
      if (ref != null) {
         entityBroker.fireEvent(eventName, ref);
      }
   }

   /**
    * Get an entity reference to any of the evaluation objects which are treated as entities
    * 
    * @param entity
    * @return an entity reference string or null if none can be found
    */
   private String getEntityReference(Serializable entity) {
      String id = null;
      try {
         Class<? extends Serializable> elementClass = entity.getClass();
         Method getIdMethod = elementClass.getMethod("getId", new Class[] {});
         Long realId = (Long) getIdMethod.invoke(entity, (Object[]) null);
         id = realId.toString();
         return getEntityReference(elementClass, id);
      } catch (Exception e) {
         log.warn("Failed to get id from entity object", e);
         return null;
      }
   }

   private String getEntityReference(Class<? extends Serializable> entityClass, String entityId) {
      String prefix = null;
      // make sure this class is supported and get the prefix
      if (entityClass == EvalEvaluation.class) {
         prefix = EvaluationEntityProvider.ENTITY_PREFIX;
      } else if (entityClass == EvalAssignGroup.class) {
         prefix = AssignGroupEntityProvider.ENTITY_PREFIX;
      } else if (entityClass == EvalAssignHierarchy.class) {
         prefix = "eval-assignhierarchy";
      } else if (entityClass == EvalGroup.class) {
         prefix = "eval-group";
      } else if (entityClass == EvalScale.class) {
         prefix = "eval-scale";
      } else if (entityClass == EvalItem.class) {
         prefix = ItemEntityProvider.ENTITY_PREFIX;
      } else if (entityClass == EvalTemplateItem.class) {
         prefix = TemplateItemEntityProvider.ENTITY_PREFIX;
      } else if (entityClass == EvalTemplate.class) {
         prefix = TemplateEntityProvider.ENTITY_PREFIX;
      } else if (entityClass == EvalResponse.class) {
         prefix = "eval-response";
      } else {
         return "eval:" + entityClass.getName();
      }

      return new IdEntityReference(prefix, entityId).toString();
   }

   /**
    * Register the various permissions
    */
   private void registerPermissions() {
      // register Sakai permissions for this tool
      functionManager.registerFunction(EvalConstants.PERM_WRITE_TEMPLATE);
      functionManager.registerFunction(EvalConstants.PERM_ASSIGN_EVALUATION);
      functionManager.registerFunction(EvalConstants.PERM_BE_EVALUATED);
      functionManager.registerFunction(EvalConstants.PERM_TAKE_EVALUATION);
   }

   /**
    * Returns the appropriate internal site type identifier based on the given string
    * 
    * @param sakaiType a type identifier used in Sakai (should be a constant from Sakai)
    * @return a CONTEXT_TYPE constant from {@link EvalConstants}
    */
   private String getContextType(String sakaiType) {
      if (sakaiType.equals(SAKAI_SITE_TYPE)) {
         return EvalConstants.GROUP_TYPE_SITE;
      } else if (sakaiType.equals(SAKAI_GROUP_TYPE)) {
         return EvalConstants.GROUP_TYPE_GROUP;
      }
      return EvalConstants.GROUP_TYPE_UNKNOWN;
   }

   /**
    * Simple method to translate one constant into another
    * (this allows the provider to have no dependency on the model)
    * @param permission a PERM constant from {@link EvalConstants}
    * @return the translated constant from {@link EvalGroupsProvider}
    */
   private String translatePermission(String permission) {
      if (EvalConstants.PERM_TAKE_EVALUATION.equals(permission)) {
         return EvalGroupsProvider.PERM_TAKE_EVALUATION;
      } else if (EvalConstants.PERM_BE_EVALUATED.equals(permission)) {
         return EvalGroupsProvider.PERM_BE_EVALUATED;
      }
      return "UNKNOWN";
   }

   /**
    * @param text string to make MD5 hash from
    * @param maxLength
    * @return an MD5 hash no longer than maxLength
    */
   private String makeMD5(String text, int maxLength) {

      MessageDigest md;
      try {
         md = MessageDigest.getInstance("MD5");
      } catch (NoSuchAlgorithmException e) {
         throw new RuntimeException("Stupid java sucks for MD5", e);
      }
      md.update(text.getBytes());

      // convert the binary md5 hash into hex
      String md5 = "";
      byte[] b_arr = md.digest();

      for (int i = 0; i < b_arr.length; i++) {
         // convert the high nibble
         byte b = b_arr[i];
         b >>>= 4;
      b &= 0x0f; // this clears the top half of the byte
      md5 += Integer.toHexString(b);

      // convert the low nibble
      b = b_arr[i];
      b &= 0x0F;
      md5 += Integer.toHexString(b);
      }
      if (maxLength > 0 && md5.length() > maxLength) {
         md5 = md5.substring(0, maxLength);
      }
      return md5;
   }


   @SuppressWarnings("unchecked")
   public <T> T getConfigurationSetting(String settingName, T defaultValue) {
      T returnValue = defaultValue;
      if (SETTING_SERVER_NAME.equals(settingName)) {
         returnValue = (T) serverConfigurationService.getServerName();
      } else if (SETTING_SERVER_URL.equals(settingName)) {
         returnValue = (T) serverConfigurationService.getServerUrl();
      } else if (SETTING_PORTAL_URL.equals(settingName)) {
         returnValue = (T) serverConfigurationService.getPortalUrl();
      } else if (SETTING_SERVER_ID.equals(settingName)) {
         returnValue = (T) serverConfigurationService.getServerIdInstance();
      } else {
         if (defaultValue == null) {
            returnValue = (T) serverConfigurationService.getString(settingName);
            if ("".equals(returnValue)) { returnValue = null; }
         } else {
            if (defaultValue instanceof Number) {
               int num = ((Number) defaultValue).intValue();
               int value = serverConfigurationService.getInt(settingName, num);
               returnValue = (T) new Integer(value);
            } else if (defaultValue instanceof Boolean) {
               boolean bool = ((Boolean) defaultValue).booleanValue();
               boolean value = serverConfigurationService.getBoolean(settingName, bool);
               returnValue = (T) new Boolean(value);
            } else if (defaultValue instanceof String) {
               returnValue = (T) serverConfigurationService.getString(settingName, (String) defaultValue);
            }
         }
      }
      return returnValue;
   }

}