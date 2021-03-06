/*
 * Changes to this file committed after and not including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
 * This file is part of Hopsworks
 * Copyright (C) 2018, Logical Clocks AB. All rights reserved
 *
 * Hopsworks is free software: you can redistribute it and/or modify it under the terms of
 * the GNU Affero General Public License as published by the Free Software Foundation,
 * either version 3 of the License, or (at your option) any later version.
 *
 * Hopsworks is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License along with this program.
 * If not, see <https://www.gnu.org/licenses/>.
 *
 * Changes to this file committed before and including commit-id: ccc0d2c5f9a5ac661e60e6eaf138de7889928b8b
 * are released under the following license:
 *
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
 */
package io.hops.hopsworks.api.project;

import io.hops.hopsworks.api.dela.DelaClusterProjectService;
import io.hops.hopsworks.api.dela.DelaProjectService;
import io.hops.hopsworks.api.filter.AllowedProjectRoles;
import io.hops.hopsworks.api.filter.NoCacheResponse;
import io.hops.hopsworks.api.jobs.JobService;
import io.hops.hopsworks.api.jobs.KafkaService;
import io.hops.hopsworks.api.jupyter.JupyterService;
import io.hops.hopsworks.api.pythonDeps.PythonDepsService;
import io.hops.hopsworks.api.serving.inference.InferenceResource;
import io.hops.hopsworks.api.tensorflow.TensorBoardService;
import io.hops.hopsworks.api.serving.TfServingService;
import io.hops.hopsworks.api.util.RESTApiJsonResponse;
import io.hops.hopsworks.api.util.LocalFsService;
import io.hops.hopsworks.common.constants.message.ResponseMessages;
import io.hops.hopsworks.common.dao.dataset.DataSetDTO;
import io.hops.hopsworks.common.dao.dataset.Dataset;
import io.hops.hopsworks.common.dao.dataset.DatasetFacade;
import io.hops.hopsworks.common.dao.dataset.DatasetPermissions;
import io.hops.hopsworks.common.dao.hdfs.inode.Inode;
import io.hops.hopsworks.common.dao.hdfs.inode.InodeFacade;
import io.hops.hopsworks.common.dao.jobs.quota.YarnPriceMultiplicator;
import io.hops.hopsworks.common.dao.project.Project;
import io.hops.hopsworks.common.dao.project.ProjectFacade;
import io.hops.hopsworks.common.dao.project.pia.Pia;
import io.hops.hopsworks.common.dao.project.pia.PiaFacade;
import io.hops.hopsworks.common.dao.project.service.ProjectServiceEnum;
import io.hops.hopsworks.common.dao.project.team.ProjectTeam;
import io.hops.hopsworks.common.dao.user.UserFacade;
import io.hops.hopsworks.common.dao.user.Users;
import io.hops.hopsworks.common.dao.user.activity.ActivityFacade;
import io.hops.hopsworks.common.dataset.DatasetController;
import io.hops.hopsworks.common.dataset.FilePreviewDTO;
import io.hops.hopsworks.common.exception.DatasetException;
import io.hops.hopsworks.common.exception.GenericException;
import io.hops.hopsworks.common.exception.HopsSecurityException;
import io.hops.hopsworks.common.exception.KafkaException;
import io.hops.hopsworks.common.exception.ProjectException;
import io.hops.hopsworks.common.exception.RESTCodes;
import io.hops.hopsworks.common.exception.ServiceException;
import io.hops.hopsworks.common.exception.UserException;
import io.hops.hopsworks.common.hdfs.DistributedFileSystemOps;
import io.hops.hopsworks.common.hdfs.DistributedFsService;
import io.hops.hopsworks.common.hdfs.HdfsUsersController;
import io.hops.hopsworks.common.message.MessageController;
import io.hops.hopsworks.common.project.MoreInfoDTO;
import io.hops.hopsworks.common.project.ProjectController;
import io.hops.hopsworks.common.project.ProjectDTO;
import io.hops.hopsworks.common.project.QuotasDTO;
import io.hops.hopsworks.common.project.TourProjectType;
import io.hops.hopsworks.common.security.CertificateMaterializer;
import io.hops.hopsworks.common.user.AuthController;
import io.hops.hopsworks.common.user.UsersController;
import io.hops.hopsworks.common.util.EmailBean;
import io.hops.hopsworks.common.util.Settings;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.net.util.Base64;

import javax.annotation.security.RolesAllowed;
import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.mail.Message;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.logging.Logger;

@Path("/project")
@RolesAllowed({"HOPS_ADMIN", "HOPS_USER"})
@Api(value = "Project Service",
    description = "Project Service")
@Produces(MediaType.APPLICATION_JSON)
@Stateless
@TransactionAttribute(TransactionAttributeType.NEVER)
public class ProjectService {

  @EJB
  private ProjectFacade projectFacade;
  @EJB
  private ProjectController projectController;
  @EJB
  private NoCacheResponse noCacheResponse;
  @Inject
  private ProjectMembersService projectMembers;
  @Inject
  private KafkaService kafka;
  @Inject
  private JupyterService jupyter;
  @Inject
  private TensorBoardService tensorboard;
  @Inject
  private TfServingService tfServingService;
  @Inject
  private DataSetService dataSet;
  @Inject
  private LocalFsService localFs;
  @Inject
  private JobService jobs;
  @Inject
  private PythonDepsService pysparkService;
  @Inject
  private CertService certs;
  @EJB
  private ActivityFacade activityFacade;
  @EJB
  private DatasetFacade datasetFacade;
  @EJB
  private DatasetController datasetController;
  @EJB
  private InodeFacade inodes;
  @EJB
  private HdfsUsersController hdfsUsersBean;
  @EJB
  private UsersController usersController;
  @EJB
  private UserFacade userFacade;
  @EJB
  private DistributedFsService dfs;
  @EJB
  private CertificateMaterializer certificateMaterializer;
  @EJB
  private MessageController messageController;
  @EJB
  private EmailBean emailBean;
  @EJB
  private AuthController authController;
  @EJB
  private PiaFacade piaFacade;
  @Inject
  private DelaProjectService delaService;
  @Inject
  private DelaClusterProjectService delaclusterService;
  @Inject
  private InferenceResource inference;

  private final static Logger LOGGER = Logger.getLogger(ProjectService.class.
      getName());

  @GET
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.ANYONE})
  public Response findAllByUser(@Context SecurityContext sc,
      @Context HttpServletRequest req) {

    // Get the user according to current session and then get all its projects
    String email = sc.getUserPrincipal().getName();
    List<ProjectTeam> list = projectController.findProjectByUser(email);
    GenericEntity<List<ProjectTeam>> projects
        = new GenericEntity<List<ProjectTeam>>(list) { };

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
        projects).build();
  }

  @GET
  @Path("/getAll")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.ANYONE})
  public Response getAllProjects(@Context SecurityContext sc,
      @Context HttpServletRequest req) {

    List<Project> list = projectFacade.findAll();
    GenericEntity<List<Project>> projects = new GenericEntity<List<Project>>(list) { };

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
        projects).build();
  }

  @GET
  @Path("/getProjectInfo/{projectName}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.ANYONE})
  public Response getProjectByName(@PathParam("projectName") String projectName,
      @Context SecurityContext sc,
      @Context HttpServletRequest req) throws ProjectException {

    ProjectDTO proj = projectController.getProjectByName(projectName);

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
        proj).build();
  }

  @GET
  @Path("/getMoreInfo/{type}/{id}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getMoreInfo(@PathParam("type") String type,
      @PathParam("id") Integer id) throws ProjectException, DatasetException {
    MoreInfoDTO info = null;
    if (id != null) {
      String errorMsg;
      switch (type) {
        case "proj":
          Project proj = projectFacade.find(id);
          if (proj == null) {
            errorMsg = "Project with id <" + id
                + "> could not be found";
            LOGGER.log(Level.WARNING, errorMsg);
            throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + id);
          }
          info = new MoreInfoDTO(proj);
          break;
        case "ds":
          info = datasetInfo(id);
          if (info == null) {
            errorMsg = "Dataset with id <" + id
                + "> could not be found";
            LOGGER.log(Level.WARNING, errorMsg);
            throw new DatasetException(RESTCodes.DatasetErrorCode.DATASET_NOT_FOUND, Level.FINE, "datasetId: " + id);
          }
          break;
      }
    }
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
        info).build();
  }

  @GET
  @Path("{id}/getMoreInfo/{type}/{inodeId}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.ANYONE})
  public Response getMoreInfo(@PathParam("id") Integer projectId, @PathParam("type") String type,
      @PathParam("inodeId") Integer id) throws ProjectException, DatasetException {
    MoreInfoDTO info = null;
    if (id != null) {
      String errorMsg;
      switch (type) {
        case "proj":
          Project proj = projectFacade.find(id);
          if (proj == null) {
            errorMsg = "Project with id <" + id
                + "> could not be found";
            LOGGER.log(Level.WARNING, errorMsg);
            throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + id);
          }
          info = new MoreInfoDTO(proj);
          break;
        case "ds":
        case "inode":
          info = inodeInfo(id, projectId);
          if (info == null) {
            errorMsg = "Dataset/INode with id <" + id
                + "> could not be found";
            LOGGER.log(Level.WARNING, errorMsg);
            throw new DatasetException(RESTCodes.DatasetErrorCode.DATASET_NOT_FOUND, Level.FINE, "datasetId: " + id);
          }
          break;
      }
    }
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
        info).build();
  }

  @GET
  @Path("/readme/byInodeId/{inodeId}")
  @Produces(MediaType.APPLICATION_JSON)
  public Response getReadmeByInodeId(@PathParam("inodeId") Integer inodeId) throws DatasetException {
    if (inodeId == null) {
      throw new IllegalArgumentException("No inodeId provided.");
    }
    Inode inode = inodes.findById(inodeId);
    Inode parent = inodes.findParent(inode);
    Project proj = projectFacade.findByName(parent.getInodePK().getName());
    Dataset ds = datasetFacade.findByProjectAndInode(proj, inode);
    if (ds != null && !ds.isSearchable()) {
      throw new DatasetException(RESTCodes.DatasetErrorCode.README_NOT_ACCESSIBLE, Level.FINE);
    }
    DistributedFileSystemOps dfso = dfs.getDfsOps();
    FilePreviewDTO filePreviewDTO;
    String path = inodes.getPath(inode);
    try {
      filePreviewDTO = datasetController.getReadme(path + "/README.md", dfso);
    } catch (IOException ex) {
      filePreviewDTO = new FilePreviewDTO();
      filePreviewDTO.setContent("No README file found for this dataset.");
      return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).
          entity(
              filePreviewDTO).build();
    }
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
        filePreviewDTO).build();
  }

  private MoreInfoDTO datasetInfo(Integer inodeId) {
    Inode inode = inodes.findById(inodeId);
    if (inode == null) {
      return null;
    }
    List<Dataset> ds = datasetFacade.findByInode(inode);
    if (ds != null && !ds.isEmpty() && !ds.get(0).isSearchable()) {
      return null;
    }
    MoreInfoDTO info = new MoreInfoDTO(inode);
    Users user = userFacade.findByUsername(info.getUser());
    info.setUser(user.getFname() + " " + user.getLname());
    info.setSize(inodes.getSize(inode));
    info.setPath(inodes.getPath(inode));
    return info;
  }

  private MoreInfoDTO inodeInfo(Integer inodeId, Integer projectId) {
    Inode inode = inodes.findById(inodeId);
    if (inode == null) {
      return null;
    }
    String group = inode.getHdfsGroup().getName();
    Project project = projectFacade.find(projectId);
    if (project != null && !project.getName().equals(hdfsUsersBean.getProjectName(group))) {
      return null;
    }
    MoreInfoDTO info = new MoreInfoDTO(inode);
    Users user = userFacade.findByUsername(info.getUser());
    info.setUser(user.getFname() + " " + user.getLname());
    info.setSize(inodes.getSize(inode));
    info.setPath(inodes.getPath(inode));
    return info;
  }

  @GET
  @Path("getDatasetInfo/{inodeId}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.ANYONE})
  public Response getDatasetInfo(
      @PathParam("inodeId") Integer inodeId,
      @Context SecurityContext sc,
      @Context HttpServletRequest req) throws DatasetException {
    Inode inode = inodes.findById(inodeId);
    Inode parent = inodes.findParent(inode);
    Project proj = projectFacade.findByName(parent.getInodePK().getName());
    Dataset ds = datasetFacade.findByProjectAndInode(proj, inode);

    if (ds == null) {
      throw new DatasetException(RESTCodes.DatasetErrorCode.DATASET_NOT_FOUND, Level.FINE, "inodeId: " + inodeId);
    }

    List<Dataset> projectsContainingInode = datasetFacade.findByInode(inode);
    List<String> sharedWith = new ArrayList<>();
    for (Dataset d : projectsContainingInode) {
      if (!d.getProject().getId().equals(proj.getId())) {
        sharedWith.add(d.getProject().getName());
      }
    }
    DataSetDTO dataset = new DataSetDTO(ds, proj, sharedWith);
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
        dataset).build();
  }

  @GET
  @Path("{id}/getInodeInfo/{inodeId}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.ANYONE})
  public Response getDatasetInfo(
      @PathParam("id") Integer projectId,
      @PathParam("inodeId") Integer inodeId,
      @Context SecurityContext sc,
      @Context HttpServletRequest req) throws ProjectException, DatasetException {
    Inode inode = inodes.findById(inodeId);
    if (inode == null) {
      throw new DatasetException(RESTCodes.DatasetErrorCode.INODE_NOT_FOUND, Level.FINE, "inodeId: " + inodeId);
    }
    Project project = projectFacade.find(projectId);
    if(project == null){
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + projectId);
    }

    DataSetDTO dataset = new DataSetDTO(inode.getInodePK().getName(), inodeId, project);
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
        dataset).build();
  }

  @GET
  @Path("{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public Response findByProjectID(
      @PathParam("id") Integer id,
      @Context SecurityContext sc,
      @Context HttpServletRequest req) throws ProjectException {

    // Get a specific project based on the id, Annotated so that 
    // only the user with the allowed role is able to see it 
    ProjectDTO proj = projectController.getProjectByID(id);

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
        proj).build();
  }

  @PUT
  @Path("{id}")
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_OWNER})
  public Response updateProject(
      ProjectDTO projectDTO,
      @PathParam("id") Integer id,
      @Context SecurityContext sc,
      @Context HttpServletRequest req)
    throws ProjectException, DatasetException, HopsSecurityException, ServiceException {

    RESTApiJsonResponse json = new RESTApiJsonResponse();
    String userEmail = sc.getUserPrincipal().getName();
    Users user = userFacade.findByEmail(userEmail);
    Project project = projectController.findProjectById(id);

    boolean updated = false;

    if (projectController.updateProjectDescription(project,
        projectDTO.getDescription(), user)) {
      json.setSuccessMessage(ResponseMessages.PROJECT_DESCRIPTION_CHANGED);
      updated = true;
    }

    if (projectController.updateProjectRetention(project,
        projectDTO.getRetentionPeriod(), user)) {
      json.setSuccessMessage(json.getSuccessMessage() + "\n" + ResponseMessages.PROJECT_RETENTON_CHANGED);
      updated = true;
    }

    if (!projectDTO.getServices().isEmpty()) {
      // Create dfso here and pass them to the different controllers
      DistributedFileSystemOps dfso = dfs.getDfsOps();
      DistributedFileSystemOps udfso = dfs.getDfsOps(hdfsUsersBean.getHdfsUserName(project, user));

      for (String s : projectDTO.getServices()) {
        ProjectServiceEnum se = null;
        se = ProjectServiceEnum.valueOf(s.toUpperCase());
        List<Future<?>> serviceFutureList = projectController.addService(project, se, user, dfso, udfso);
        if (serviceFutureList != null) {
          // Wait for the futures
          for (Future f : serviceFutureList) {
            try {
              f.get();
            } catch (InterruptedException | ExecutionException e) {
              throw new ServiceException(RESTCodes.ServiceErrorCode.SERVICE_GENERIC_ERROR,
                  Level.SEVERE, "service: " + s, e.getMessage(), e);
            }
          }

          // Service successfully enabled
          json.setSuccessMessage(json.getSuccessMessage() + "\n"
            + ResponseMessages.PROJECT_SERVICE_ADDED
            + s
          );
          updated = true;
        }
      }

      // close dfsos
      if (dfso != null) {
        dfso.close();
      }
      if (udfso != null) {
        dfs.closeDfsClient(udfso);
      }
    }

    if (!updated) {
      json.setSuccessMessage(ResponseMessages.NOTHING_TO_UPDATE);
    }

    return noCacheResponse.getNoCacheResponseBuilder(
        Response.Status.CREATED).entity(json).build();
  }

  private void populateActiveServices(List<String> projectServices,
      TourProjectType tourType) {
    for (ProjectServiceEnum service : tourType.getActiveServices()) {
      projectServices.add(service.name());
    }
  }

  @POST
  @Path("starterProject/{type}")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.ANYONE})
  public Response example(
      @PathParam("type") String type,
      @Context SecurityContext sc,
      @Context HttpServletRequest req)
    throws DatasetException, GenericException, KafkaException, ProjectException, UserException,
    ServiceException, HopsSecurityException {
    if (!Arrays.asList(TourProjectType.values()).contains(TourProjectType.valueOf(type.toUpperCase()))) {
      throw new IllegalArgumentException("Type must be one of: " + Arrays.toString(TourProjectType.values()));
    }
    
    ProjectDTO projectDTO = new ProjectDTO();
    Project project = null;
    projectDTO.setDescription("A demo project for getting started with " + type);

    String owner = sc.getUserPrincipal().getName();
    String username = usersController.generateUsername(owner);
    List<String> projectServices = new ArrayList<>();
    Users user = userFacade.findByEmail(owner);
    //save the project
    List<String> failedMembers = new ArrayList<>();

    TourProjectType demoType = null;
    String readMeMessage = null;
    if (TourProjectType.SPARK.getTourName().equalsIgnoreCase(type)) {
      // It's a Spark guide
      demoType = TourProjectType.SPARK;
      projectDTO.setProjectName("demo_" + TourProjectType.SPARK.getTourName() + "_" + username);
      populateActiveServices(projectServices, TourProjectType.SPARK);
      readMeMessage = "jar file to demonstrate the creation of a spark batch job";
    } else if (TourProjectType.KAFKA.getTourName().equalsIgnoreCase(type)) {
      // It's a Kafka guide
      demoType = TourProjectType.KAFKA;
      projectDTO.setProjectName("demo_" + TourProjectType.KAFKA.getTourName() + "_" + username);
      populateActiveServices(projectServices, TourProjectType.KAFKA);
      readMeMessage = "jar file to demonstrate Kafka streaming";
    }  else if (TourProjectType.DEEP_LEARNING.getTourName().equalsIgnoreCase(type)) {
      // It's a TensorFlow guide
      demoType = TourProjectType.DEEP_LEARNING;
      projectDTO.setProjectName("demo_" + TourProjectType.DEEP_LEARNING.getTourName() + "_" + username);
      populateActiveServices(projectServices, TourProjectType.DEEP_LEARNING);
      readMeMessage = "Jupyter notebooks and training data for demonstrating how to run Deep Learning";
    }
    projectDTO.setServices(projectServices);

    DistributedFileSystemOps dfso = null;
    DistributedFileSystemOps udfso = null;
    try {
      project = projectController.createProject(projectDTO, user, failedMembers, req.getSession().getId());
      dfso = dfs.getDfsOps();
      username = hdfsUsersBean.getHdfsUserName(project, user);
      udfso = dfs.getDfsOps(username);
      projectController.addTourFilesToProject(owner, project, dfso, dfso, demoType);
      //TestJob dataset
      datasetController.generateReadme(udfso, "TestJob", readMeMessage, project.getName());
    } catch (Exception ex) {
      projectController.cleanup(project, req.getSession().getId());
      throw ex;
    } finally {
      if (dfso != null) {
        dfso.close();
      }
      if (udfso != null) {
        dfs.closeDfsClient(udfso);
      }
    }
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.CREATED).
        entity(project).build();
  }

  @POST
  @Produces(MediaType.APPLICATION_JSON)
  @Consumes(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.ANYONE})
  public Response createProject(
      ProjectDTO projectDTO,
      @Context SecurityContext sc,
      @Context HttpServletRequest req)
    throws DatasetException, GenericException, KafkaException, ProjectException, UserException, ServiceException,
    HopsSecurityException {

    //check the user
    Users user = userFacade.findByEmail(sc.getUserPrincipal().getName());
    List<String> failedMembers = null;
    projectController.createProject(projectDTO, user, failedMembers, req.getSession().getId());

    RESTApiJsonResponse json = new RESTApiJsonResponse();
    StringBuilder message = new StringBuilder();
    message.append(ResponseMessages.PROJECT_CREATED);
    message.append("<br>You have ").append(user.getMaxNumProjects() - user.getNumCreatedProjects()).
        append(" project(s) left that you can create");
    json.setSuccessMessage(message.toString());

    if (failedMembers != null && !failedMembers.isEmpty()) {
      json.setFieldErrors(failedMembers);
    }
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.CREATED).
        entity(json).build();
  }

  @POST
  @Path("{id}/delete")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_OWNER})
  public Response removeProjectAndFiles(
      @PathParam("id") Integer id,
      @Context SecurityContext sc,
      @Context HttpServletRequest req) throws ProjectException, GenericException {

    String userMail = sc.getUserPrincipal().getName();
    RESTApiJsonResponse json = new RESTApiJsonResponse();

    projectController.removeProject(userMail, id, req.getSession().getId());

    json.setSuccessMessage(ResponseMessages.PROJECT_REMOVED);
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).
        entity(json).build();

  }

  @Path("{id}/projectMembers")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public ProjectMembersService projectMembers(
      @PathParam("id") Integer id) {
    this.projectMembers.setProjectId(id);

    return this.projectMembers;
  }

  @Path("{id}/dataset")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public DataSetService datasets(
      @PathParam("id") Integer id) throws ProjectException {
    Project project = projectController.findProjectById(id);
    if (project == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + id);
    }
    this.dataSet.setProjectId(id);

    return this.dataSet;
  }

  @Path("{id}/localfs")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public LocalFsService localFs(
      @PathParam("id") Integer id) {
    this.localFs.setProjectId(id);

    return this.localFs;
  }

  @Path("{projectId}/jobs")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_OWNER, AllowedProjectRoles.DATA_SCIENTIST})
  public JobService jobs(@PathParam("projectId") Integer projectId) throws ProjectException {
    Project project = projectController.findProjectById(projectId);
    if (project == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + projectId);
    }
    return this.jobs.setProject(project);
  }

  @Path("{projectId}/certs")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_OWNER, AllowedProjectRoles.DATA_SCIENTIST})
  public CertService certs(@PathParam("projectId") Integer projectId) throws ProjectException {
    Project project = projectController.findProjectById(projectId);
    if (project == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + projectId);
    }
    return this.certs.setProject(project);
  }

  @GET
  @Path("{id}/quotas")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public Response quotasByProjectID(
      @PathParam("id") Integer id,
      @Context SecurityContext sc,
      @Context HttpServletRequest req) throws ProjectException {

    QuotasDTO quotas = projectController.getQuotas(id);

    // If YARN quota or HDFS quota for project directory is null, something is wrong with the project
    // throw a ProjectException
    if (quotas.getHdfsQuotaInBytes() == null || quotas.getYarnQuotaInSecs() == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.QUOTA_NOT_FOUND, Level.FINE, "projectId: " + id);
    }

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(
        quotas).build();
  }

  @GET
  @Path("{id}/multiplicators")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public Response getCurrentMultiplicator(
      @PathParam("id") Integer id,
      @Context SecurityContext sc,
      @Context HttpServletRequest req) {

    List<YarnPriceMultiplicator> multiplicatorsList = projectController.getYarnMultiplicators();

    GenericEntity<List<YarnPriceMultiplicator>> multiplicators = new GenericEntity<List<YarnPriceMultiplicator>>(
        multiplicatorsList) { };
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(multiplicators).build();
  }

  @GET
  @Path("{id}/importPublicDataset/{projectName}/{inodeId}")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_OWNER})
  public Response quotasByProjectID(
      @PathParam("id") Integer id,
      @PathParam("projectName") String projectName,
      @PathParam("inodeId") Integer dsId,
      @Context SecurityContext sc,
      @Context HttpServletRequest req) throws ProjectException, DatasetException {

    Project destProj = projectController.findProjectById(id);
    Project dsProject = projectFacade.findByName(projectName);

    Inode inode = inodes.findById(dsId);
    Dataset ds = datasetFacade.findByProjectAndInode(dsProject, inode);
    if (ds == null) {
      throw new DatasetException(RESTCodes.DatasetErrorCode.DATASET_NOT_FOUND, Level.FINE, "project: " + projectName
        + ", inodeId:" + dsId);
    }

    if (ds.isPublicDs() == false) {
      throw new DatasetException(RESTCodes.DatasetErrorCode.DATASET_NOT_PUBLIC, Level.FINE, "datasetId: " + ds.getId());
    }

    Dataset newDS = new Dataset(inode, destProj);
    newDS.setShared(true);

    if (ds.getDescription() != null) {
      newDS.setDescription(ds.getDescription());
    }
    if (ds.isPublicDs()) {
      newDS.setPublicDs(ds.getPublicDs());
    }
    newDS.setEditable(DatasetPermissions.OWNER_ONLY);
    datasetFacade.persistDataset(newDS);
    Users user = userFacade.findByEmail(sc.getUserPrincipal().getName());

    activityFacade.persistActivity(ActivityFacade.SHARED_DATA + newDS.toString()
        + " with project " + destProj.getName(), destProj, user);

    hdfsUsersBean.shareDataset(destProj, ds);

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).build();
  }

  @POST
  @Path("{id}/downloadCert")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_OWNER})
  public Response downloadCerts(@PathParam("id") Integer id, @FormParam("password") String password,
      @Context HttpServletRequest req) throws ProjectException, HopsSecurityException, DatasetException {
    Users user = userFacade.findByEmail(req.getRemoteUser());
    if (user.getEmail().equals(Settings.AGENT_EMAIL) ||
        !authController.validatePwd(user, password, req)) {
      throw new HopsSecurityException(RESTCodes.SecurityErrorCode.CERT_ACCESS_DENIED, Level.FINE);
    }
    Project project = projectController.findProjectById(id);
    String keyStore = "";
    String trustStore = "";
    try {
      //Read certs from database and stream them out
      certificateMaterializer.materializeCertificatesLocal(user.getUsername(), project.getName());
      CertificateMaterializer.CryptoMaterial material = certificateMaterializer.getUserMaterial(user.getUsername(),
          project.getName());
      keyStore = Base64.encodeBase64String(material.getKeyStore().array());
      trustStore = Base64.encodeBase64String(material.getTrustStore().array());
      String certPwd = new String(material.getPassword());
      //Pop-up a message from admin
      messageController.send(user, userFacade.findByEmail(Settings.SITE_EMAIL), "Certificate Info", "",
          "An email was sent with the password for your project's certificates. If an email does not arrive shortly, "
          + "please check spam first and then contact the administrator.", "");
      emailBean.sendEmail(user.getEmail(), Message.RecipientType.TO, "Hopsworks certificate information",
          "The password for keystore and truststore is:" + certPwd);
    } catch (Exception ex) {
      LOGGER.log(Level.SEVERE, null, ex);
      throw new DatasetException(RESTCodes.DatasetErrorCode.DOWNLOAD_ERROR, Level.SEVERE, "projectId: " + id,
        ex.getMessage(), ex);
    } finally {
      certificateMaterializer.removeCertificatesLocal(user.getUsername(), project.getName());
    }
    CertsDTO certsDTO = new CertsDTO("jks", keyStore, trustStore);
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(certsDTO).build();
  }

  @Path("{id}/kafka")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public KafkaService kafka(
      @PathParam("id") Integer id) throws ProjectException {
    Project project = projectController.findProjectById(id);
    if (project == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + id);
    }
    this.kafka.setProject(project);

    return this.kafka;
  }

  @Path("{id}/jupyter")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public JupyterService jupyter(
      @PathParam("id") Integer id) throws ProjectException {
    Project project = projectController.findProjectById(id);
    if (project == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + id);
    }
    this.jupyter.setProjectId(id);

    return this.jupyter;
  }

  @Path("{id}/tensorboard")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public TensorBoardService tensorboard(
          @PathParam("id") Integer id) throws ProjectException {
    Project project = projectController.findProjectById(id);
    if (project == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + id);
    }
    this.tensorboard.setProjectId(id);

    return this.tensorboard;
  }

  @Path("{id}/serving")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public TfServingService tfServingService(
      @PathParam("id") Integer id,
      @Context SecurityContext sc) throws ProjectException {
    Project project = projectController.findProjectById(id);
    if (project == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + id);
    }
    Users user = userFacade.findByEmail(sc.getUserPrincipal().getName());
    this.tfServingService.setProject(project);
    this.tfServingService.setUser(user);

    return this.tfServingService;
  }

  @Path("{id}/pythonDeps")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_OWNER, AllowedProjectRoles.DATA_SCIENTIST})
  public PythonDepsService pysparkDeps(@PathParam("id") Integer id) throws ProjectException {
    Project project = projectController.findProjectById(id);
    if (project == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + id);
    }
    this.pysparkService.setProject(project);
    return pysparkService;
  }

  @Path("{id}/dela")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public DelaProjectService dela(@PathParam("id") Integer id) throws ProjectException {
    Project project = projectController.findProjectById(id);
    if (project == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + id);
    }
    this.delaService.setProjectId(id);

    return this.delaService;
  }

  @Path("{id}/delacluster")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public DelaClusterProjectService delacluster(@PathParam("id") Integer id) throws ProjectException {
    Project project = projectController.findProjectById(id);
    if (project == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND,  Level.FINE, "projectId: " + id);
    }
    this.delaclusterService.setProjectId(id);

    return this.delaclusterService;
  }

  @PUT
  @Path("{id}/pia")
  @Consumes(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_OWNER})
  public Response updatePia(
      Pia pia,
      @PathParam("id") Integer projectId,
      @Context SecurityContext sc,
      @Context HttpServletRequest req) {
    
    piaFacade.mergeUpdate(pia, projectId);
    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).build();
  }

  @GET
  @Path("{id}/pia")
  @Produces(MediaType.APPLICATION_JSON)
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public Response getPia(@Context SecurityContext sc,
      @PathParam("id") Integer projectId,
      @Context HttpServletRequest req) throws ProjectException {
    
    Project project = projectController.findProjectById(projectId);
    if (project == null) {
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + projectId);
    }
    Pia pia = piaFacade.findByProject(projectId);
    GenericEntity<Pia> genericPia = new GenericEntity<Pia>(pia) {};

    return noCacheResponse.getNoCacheResponseBuilder(Response.Status.OK).entity(genericPia).build();
  }

  @ApiOperation(value = "Model inference sub-resource", tags = {"Inference"})
  @Path("/{projectId}/inference")
  @AllowedProjectRoles({AllowedProjectRoles.DATA_SCIENTIST, AllowedProjectRoles.DATA_OWNER})
  public InferenceResource infer(@PathParam("projectId") Integer projectId, @Context SecurityContext sc)
      throws ProjectException {
    Project project = projectFacade.find(projectId);
    if (project == null){
      throw new ProjectException(RESTCodes.ProjectErrorCode.PROJECT_NOT_FOUND, Level.FINE, "projectId: " + projectId);
    }
    inference.setProject(project);
    return inference;
  }

}
