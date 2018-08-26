/*
 * Copyright (C) 2013 - 2018, Logical Clocks AB and RISE SICS AB. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this
 * software and associated documentation files (the "Software"), to deal in the Software
 * without restriction, including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software, and to permit
 * persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS  OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL  THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR  OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package io.hops.hopsworks.api.user;

import io.hops.hopsworks.api.filter.AllowedProjectGroups;
import io.hops.hopsworks.api.filter.NoCacheResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.codec.binary.Base64;
import io.hops.hopsworks.api.filter.AllowedProjectRoles;
import io.hops.hopsworks.api.util.JsonResponse;
import io.hops.hopsworks.common.constants.message.ResponseMessages;
import io.hops.hopsworks.common.dao.project.team.ProjectTeam;
import io.hops.hopsworks.common.dao.user.UserCardDTO;
import io.hops.hopsworks.common.dao.user.UserDTO;
import io.hops.hopsworks.common.dao.user.UserFacade;
import io.hops.hopsworks.common.dao.user.UserProjectDTO;
import io.hops.hopsworks.common.dao.user.Users;
import io.hops.hopsworks.common.dao.user.sshkey.SshKeyDTO;
import io.hops.hopsworks.common.exception.AppException;
import io.hops.hopsworks.common.project.ProjectController;
import io.hops.hopsworks.common.user.UsersController;
import io.swagger.annotations.Api;
import io.hops.hopsworks.api.filter.JWTokenNeeded;
import javax.mail.MessagingException;

@Path("/user")
@Stateless
@Api(value = "User", description = "User service")
@TransactionAttribute(TransactionAttributeType.NEVER)
public class UserService {

  private final static Logger logger = Logger.getLogger(UserService.class.
          getName());

  @EJB
  private UserFacade userBean;
  @EJB
  private UsersController userController;
  @EJB
  private NoCacheResponse noCacheResponse;
  @EJB
  private ProjectController projectController;

  @GET
  @Path("allcards")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectGroups({AllowedProjectGroups.HOPS_ADMIN, AllowedProjectGroups.HOPS_USER})
  @AllowedProjectRoles({AllowedProjectRoles.ANYONE})
  @JWTokenNeeded
  public Response findAllByUser(@Context SecurityContext sc,
          @Context HttpServletRequest req) {

    List<Users> users = userBean.findAllUsers();
    List<UserCardDTO> userCardDTOs = new ArrayList<>();

    for (Users user : users) {
      UserCardDTO userCardDTO = new UserCardDTO(user);
      userCardDTOs.add(userCardDTO);
    }
    GenericEntity<List<UserCardDTO>> userCards
            = new GenericEntity<List<UserCardDTO>>(userCardDTOs) {};
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
            userCards).build();
  }

  @GET
  @Path("profile")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectGroups({AllowedProjectGroups.HOPS_ADMIN, AllowedProjectGroups.HOPS_USER})
  @JWTokenNeeded
  public Response getUserProfile(@Context SecurityContext sc) throws
          AppException {
    Users user = userBean.findByEmail(sc.getUserPrincipal().getName());

    if (user == null) {
      throw new AppException(Response.Status.NOT_FOUND.getStatusCode(),
              ResponseMessages.USER_WAS_NOT_FOUND);
    }

    UserDTO userDTO = new UserDTO(user);

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
            userDTO).build();
  }

  @POST
  @Path("updateProfile")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectGroups({AllowedProjectGroups.HOPS_ADMIN, AllowedProjectGroups.HOPS_USER})
  @JWTokenNeeded
  public Response updateProfile(@FormParam("firstName") String firstName,
          @FormParam("lastName") String lastName,
          @FormParam("telephoneNum") String telephoneNum,
          @FormParam("toursState") Integer toursState,
          @Context HttpServletRequest req) throws AppException {
    JsonResponse json = new JsonResponse();
    
    Users user = userController.updateProfile(req.getRemoteUser(), firstName, lastName, telephoneNum, toursState, req);
    UserDTO userDTO = new UserDTO(user);
    
    json.setStatus("OK");
    json.setSuccessMessage(ResponseMessages.PROFILE_UPDATED);
    json.setData(userDTO);

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
            userDTO).build();
  }

  @POST
  @Path("changeLoginCredentials")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectGroups({AllowedProjectGroups.HOPS_ADMIN, AllowedProjectGroups.HOPS_USER})
  @JWTokenNeeded
  public Response changeLoginCredentials(
          @FormParam("oldPassword") String oldPassword,
          @FormParam("newPassword") String newPassword,
          @FormParam("confirmedPassword") String confirmedPassword,
          @Context HttpServletRequest req) throws AppException, MessagingException {
    JsonResponse json = new JsonResponse();

    userController.changePassword(req.getRemoteUser(), oldPassword, newPassword, confirmedPassword, req);

    json.setStatus("OK");
    json.setSuccessMessage(ResponseMessages.PASSWORD_CHANGED);

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
            json).build();
  }

  @POST
  @Path("changeSecurityQA")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectGroups({AllowedProjectGroups.HOPS_ADMIN, AllowedProjectGroups.HOPS_USER})
  @JWTokenNeeded
  public Response changeSecurityQA(@FormParam("oldPassword") String oldPassword,
          @FormParam("securityQuestion") String securityQuestion,
          @FormParam("securityAnswer") String securityAnswer,
          @Context HttpServletRequest req) throws AppException, MessagingException {
    JsonResponse json = new JsonResponse();
    userController.changeSecQA(req.getRemoteUser(), oldPassword, securityQuestion, securityAnswer, req);

    json.setStatus("OK");
    json.setSuccessMessage(ResponseMessages.SEC_QA_CHANGED);

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
            json).build();
  }

  @POST
  @Path("changeTwoFactor")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectGroups({AllowedProjectGroups.HOPS_ADMIN, AllowedProjectGroups.HOPS_USER})
  @JWTokenNeeded
  public Response changeTwoFactor(@FormParam("password") String password,
          @FormParam("twoFactor") boolean twoFactor,
          @Context HttpServletRequest req) throws AppException {
    Users user = userBean.findByEmail(req.getRemoteUser());

    byte[] qrCode;
    JsonResponse json = new JsonResponse();
    if (user.getTwoFactor() == twoFactor) {
      json.setSuccessMessage("No change made.");
      json.setStatus("OK");
      return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).
              entity(json).build();
    }

    qrCode = userController.changeTwoFactor(user, password, req);
    if (qrCode != null) {
      json.setQRCode(new String(Base64.encodeBase64(qrCode)));
    } else {
      json.setSuccessMessage("Tow factor authentication disabled.");
    }
    json.setStatus("OK");
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).
            entity(json).build();
  }

  @POST
  @Path("getQRCode")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectGroups({AllowedProjectGroups.HOPS_ADMIN, AllowedProjectGroups.HOPS_USER})
  @JWTokenNeeded
  public Response getQRCode(@FormParam("password") String password,
          @Context SecurityContext sc,
          @Context HttpServletRequest req) throws AppException {
    Users user = userBean.findByEmail(sc.getUserPrincipal().getName());
    if (user == null) {
      throw new AppException(Response.Status.NOT_FOUND.getStatusCode(), ResponseMessages.USER_WAS_NOT_FOUND);
    }
    if (password == null || password.isEmpty()) {
      throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), "Password requierd.");
    }
    byte[] qrCode;
    JsonResponse json = new JsonResponse();
    qrCode = userController.getQRCode(user, password, req);
    if (qrCode != null) {
      json.setQRCode(new String(Base64.encodeBase64(qrCode)));
    } else {
      throw new AppException(Response.Status.BAD_REQUEST.getStatusCode(), "Two factor disabled.");
    }
    json.setStatus("OK");
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(json).build();
  }

  @POST
  @Path("addSshKey")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectGroups({AllowedProjectGroups.HOPS_ADMIN, AllowedProjectGroups.HOPS_USER})
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTokenNeeded
  public Response addSshkey(SshKeyDTO sshkey,
          @Context SecurityContext sc,
          @Context HttpServletRequest req) throws AppException {
    Users user = userBean.findByEmail(sc.getUserPrincipal().getName());
    int id = user.getUid();
    SshKeyDTO dto = userController.addSshKey(id, sshkey.getName(), sshkey.
            getPublicKey());
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
            dto).build();
  }

  @POST
  @Path("removeSshKey")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectGroups({AllowedProjectGroups.HOPS_ADMIN, AllowedProjectGroups.HOPS_USER})
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTokenNeeded
  public Response removeSshkey(@FormParam("name") String name,
          @Context SecurityContext sc,
          @Context HttpServletRequest req) throws AppException {
    JsonResponse json = new JsonResponse();
    Users user = userBean.findByEmail(sc.getUserPrincipal().getName());
    int id = user.getUid();
    userController.removeSshKey(id, name);
    json.setStatus("OK");
    json.setSuccessMessage(ResponseMessages.SSH_KEY_REMOVED);
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
            json).build();
  }

  @GET
  @Path("getSshKeys")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectGroups({AllowedProjectGroups.HOPS_ADMIN, AllowedProjectGroups.HOPS_USER})
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  @JWTokenNeeded
  public Response getSshkeys(@Context SecurityContext sc,
          @Context HttpServletRequest req) throws AppException {
    Users user = userBean.findByEmail(sc.getUserPrincipal().getName());
    int id = user.getUid();
    List<SshKeyDTO> sshKeys = userController.getSshKeys(id);

    GenericEntity<List<SshKeyDTO>> sshKeyViews
            = new GenericEntity<List<SshKeyDTO>>(sshKeys) {};
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
            sshKeyViews).build();

  }

  @POST
  @Path("getRole")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectGroups({AllowedProjectGroups.HOPS_ADMIN, AllowedProjectGroups.HOPS_USER})
  @JWTokenNeeded
  public Response getRole(@FormParam("projectId") int projectId,
          @Context SecurityContext sc,
          @Context HttpServletRequest req) throws AppException {
    String email = sc.getUserPrincipal().getName();

    UserProjectDTO userDTO = new UserProjectDTO();
    userDTO.setEmail(email);
    userDTO.setProject(projectId);

    List<ProjectTeam> list = projectController.findProjectTeamById(projectId);

    for (ProjectTeam pt : list) {
      logger.log(Level.FINEST, "{0} ({1}) -  {2}", new Object[]{pt.
        getProjectTeamPK().getTeamMember(),
        pt.getProjectTeamPK().getProjectId(), pt.getTeamRole()});
      if (pt.getProjectTeamPK().getTeamMember().compareToIgnoreCase(email) == 0) {
        userDTO.setRole(pt.getTeamRole());
      }
    }

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
            userDTO).build();
  }

}
