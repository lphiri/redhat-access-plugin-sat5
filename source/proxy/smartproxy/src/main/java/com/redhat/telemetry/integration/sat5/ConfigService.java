package com.redhat.telemetry.integration.sat5;

import java.io.File;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.CookieParam;
import javax.ws.rs.ForbiddenException;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.commons.configuration.ConfigurationException;

@Path("/config")
public class ConfigService {
  @Context ServletContext context;


  /**
   * Retrieve general config values
   */
  @GET
  @Path("/general")
  @Produces(MediaType.APPLICATION_JSON)
  public Config getConfig(
      @CookieParam("pxt-session-cookie") String sessionKey,
      @QueryParam("satellite_user") String satelliteUser) 
          throws ConfigurationException, MalformedURLException {

    if (userIsAdmin(sessionKey, satelliteUser)) {
      PropertiesConfiguration properties = new PropertiesConfiguration();
      properties.load(context.getResourceAsStream(Constants.PROPERTIES_URL));
      String username = properties.getString(Constants.USERNAME_PROPERTY);
      boolean enabled = properties.getBoolean(Constants.ENABLED_PROPERTY);
      Config config = new Config(enabled, username, "");
      return config;
    } else {
      throw new ForbiddenException("Must be satellite admin.");
    }   
  }

  /**
   * Update general config values
   */
  @POST
  @Path("/general")
  @Consumes(MediaType.APPLICATION_JSON)
  public Response postConfig(
      Config config,
      @CookieParam("pxt-session-cookie") String sessionKey,
      @QueryParam("satellite_user") String satelliteUser) 
          throws ConfigurationException, MalformedURLException {

    if (!userIsAdmin(sessionKey, satelliteUser)) {
      throw new ForbiddenException("Must be satellite admin.");
    }
    if (config.getEnabled()) {
      createRepo(sessionKey);
      createChannel(sessionKey);
      createConfigChannel(sessionKey);
    } else {
      //TODO: push config to disable reporting?
    }
    PropertiesConfiguration properties = new PropertiesConfiguration();
    properties.setFile(new File(context.getRealPath(Constants.PROPERTIES_URL)));
    properties.setProperty(Constants.ENABLED_PROPERTY, config.getEnabled());
    properties.setProperty(Constants.USERNAME_PROPERTY, config.getUsername());
    properties.setProperty(Constants.PASSWORD_PROPERTY, config.getPassword());
    properties.save();
    return Response.status(200).build();
  }

  /**
   * Retrieve a list of systems visible to the user
   * Includes the installation status for each system
   */
  @SuppressWarnings("unchecked")
  @GET
  @Path("/systems")
  @Produces(MediaType.APPLICATION_JSON)
  public ArrayList<SatSystem> getSystems(
      @CookieParam("pxt-session-cookie") String sessionKey) {

    Object[] apiSystems = SatApi.listSystems(sessionKey);
    ArrayList<SatSystem> satSystems = new ArrayList<SatSystem>();
    for (Object apiSys : apiSystems) {
      HashMap<Object, Object> apiSysMap = (HashMap<Object, Object>) apiSys;
      Object systemDetails = SatApi.getSystemDetails(sessionKey, (int) apiSysMap.get("id"));
      HashMap<Object, Object> systemDetailsMap = (HashMap<Object, Object>) systemDetails;
      String systemVersion = (String) systemDetailsMap.get("release");
      int systemId = (int) apiSysMap.get("id");
      SystemInstallStatus installationStatus = new SystemInstallStatus();
      boolean validSystem = false;
      boolean enabled = false;
      if (systemVersion.equals("6Server") || systemVersion.equals("7Server")) {
        validSystem = true;
        if (channelExists(sessionKey)) {
          if (rpmInstalled(sessionKey, systemId)) {
            installationStatus.setRpmInstalled(true);
            enabled = true;
          }
          if (softwareChannelAssociated(sessionKey, systemId)) {
            installationStatus.setSoftwareChannelAssociated(true);
            enabled = true;
          }
          if (configChannelAssociated(sessionKey, systemId)) {
            installationStatus.setConfigChannelAssociated(true);
            enabled = true;
          }
          if (configDeployed(sessionKey, systemId)) {
            installationStatus.setConfigDeployed(true);
            enabled = true;
          }
        }
      }

      SatSystem satSys = new SatSystem(
          systemId,
          (String) apiSysMap.get("name"),
          systemVersion,
          installationStatus,
          enabled,
          validSystem);
      satSystems.add(satSys);
    }

    return satSystems;
  }

  /**
   * (Un)Install insights on multiple systems
   */
  @SuppressWarnings("unchecked")
  @POST
  @Path("/systems")
  @Consumes(MediaType.APPLICATION_JSON)
  public ArrayList<SatSystem> postSystems(
      @CookieParam("pxt-session-cookie") String sessionKey,
      ArrayList<SatSystem> systems) {

    //grab the redhat-access-proactive packageId from the channel
    Object[] channelPackages = 
      SatApi.listAllPackagesInChannel(sessionKey, Constants.CHANNEL_LABEL);
    int packageId = -1;
    for (Object channelPackage : channelPackages) {
      HashMap<Object, Object> channelPackageMap = (HashMap<Object, Object>) channelPackage;
      String packageName = (String) channelPackageMap.get("name");
      if (packageName.equals(Constants.PACKAGE_NAME)) {
        packageId = (int) channelPackageMap.get("id");
      }
    }

    for (SatSystem sys : systems) {
      if (sys.getValidType()) {
        if (sys.getEnabled()) { //install missing pieces
          setSystemSoftwareChannels(sessionKey, sys.getId(), true);

          //install the package
          if (!rpmInstalled(sessionKey, sys.getId())) {;
            ArrayList<Integer> packageIds = new ArrayList<Integer>();
            packageIds.add(packageId);
            SatApi.schedulePackageInstall(sessionKey, sys.getId(), packageIds, 60000);
          }

          //subscribe system to Red Hat Insights config channel
          if (!configChannelAssociated(sessionKey, sys.getId())) {
            ArrayList<Integer> systemIds = new ArrayList<Integer>();
            systemIds.add(sys.getId());
            ArrayList<String> channelLabels = new ArrayList<String>();
            channelLabels.add(Constants.CONFIG_CHANNEL_LABEL);
            SatApi.addConfigChannelsToSystem(sessionKey, systemIds, channelLabels, true);
          }
        } else { //remove installed pieces
          if (softwareChannelAssociated(sessionKey, sys.getId())) {
            setSystemSoftwareChannels(sessionKey, sys.getId(), false);
          }
          if (rpmInstalled(sessionKey, sys.getId())) {
            ArrayList<Integer> packageIds = new ArrayList<Integer>();
            packageIds.add(packageId);
            SatApi.schedulePackageRemove(sessionKey, sys.getId(), packageIds);
          }
          if (configChannelAssociated(sessionKey, sys.getId())) {
            ArrayList<Integer> systemIds = new ArrayList<Integer>();
            systemIds.add(sys.getId());
            ArrayList<String> channelLabels = new ArrayList<String>();
            channelLabels.add(Constants.CONFIG_CHANNEL_LABEL);
            SatApi.removeConfigChannelsFromSystem(sessionKey, systemIds, channelLabels);
          }
        }
      }
    }
    SatApi.deployAllSystems(sessionKey, Constants.CONFIG_CHANNEL_LABEL);
    return systems;
  };

  /**
   * Check if a system has the insights channel associated
   */
  @SuppressWarnings("unchecked")
  private boolean softwareChannelAssociated(String sessionKey, int systemId) {
    Object[] channels = SatApi.listSystemChannels(sessionKey, systemId);
    boolean found = false;
    if (channels != null) {
      for (Object channel : channels) {
        HashMap<Object, Object> channelMap = (HashMap<Object, Object>) channel;
        if (channelMap.get("label").equals(Constants.CHANNEL_LABEL)) {
          found = true;
        }
      }
    }
    return found;
  }

  /**
   * Check if a system has the config channel associated
   */
  @SuppressWarnings("unchecked")
  private boolean configChannelAssociated(String sessionKey, int systemId) {
    Object[] channels = SatApi.listConfigChannels(sessionKey, systemId);
    boolean found = false;
    if (channels != null) {
      for (Object channel : channels) {
        HashMap<Object, Object> channelMap = (HashMap<Object, Object>) channel;
        if (channelMap.get("label").equals(Constants.CONFIG_CHANNEL_LABEL)) {
          found = true;
        }
      }
    }
    return found;
  }

  /**
   * Get the latest insights config file revision
   */
  @SuppressWarnings("unchecked")
  private int getLatestFileRevision(String sessionKey) {
    Object[] revisions =
      (Object[]) SatApi.getFileRevisions(
          sessionKey, Constants.CONFIG_CHANNEL_LABEL, Constants.CONFIG_PATH);
    int version = -1;
    if (revisions != null) {
      for (Object revision : revisions) {
        HashMap<Object, Object> revisionMap = (HashMap<Object, Object>) revision;
        int currentVersion = (int) revisionMap.get("revision");
        if (currentVersion > version) {
          version = currentVersion;
        }
      }
    }
    return version;
  }

  /**
   * Check if a system has the latest config file rev deployed
   */
  @SuppressWarnings("unchecked")
  private boolean configDeployed(String sessionKey, int systemId) {
    boolean response = false;
    int latestRevision = getLatestFileRevision(sessionKey);
    if (latestRevision > 0) {
      ArrayList<String> paths = new ArrayList<String>();
      paths.add(Constants.CONFIG_PATH);
      Object[] fileInfos = SatApi.lookupFileInfo(sessionKey, systemId, paths, 1);
      if (fileInfos != null) {
        for (Object fileInfo : fileInfos) {
          HashMap<Object, Object> fileInfoMap = (HashMap<Object, Object>) fileInfo;
          String channel = (String) fileInfoMap.get("channel");
          if (channel.equals(Constants.CONFIG_CHANNEL_NAME)) {
            int revision = (int) fileInfoMap.get("revision");
            if (revision == latestRevision) {
              response = true;
            }
          }
        }
      }
    }

    return response;
  }

  /**
   * Check if a system has the RPM installed
   */
  @SuppressWarnings("unchecked")
  private boolean rpmInstalled(String sessionKey, int systemId) {
    Object[] installedPackages = 
      SatApi.listInstalledPackagesFromChannel(sessionKey, systemId, Constants.CHANNEL_LABEL);
    boolean found = false;
    if (installedPackages != null) {
      for (Object installedPackage : installedPackages) {
        HashMap<Object, Object> packageMap = (HashMap<Object, Object>) installedPackage;
        found = true;
      }
    }
    return found;
  }

  /**
   * Check if the insights software channel exists
   */
  @SuppressWarnings("unchecked")
  private boolean channelExists(String sessionKey) {
    Object[] channels = SatApi.listSoftwareChannels(sessionKey);
    boolean response = false;
    if (channels != null) {
      for (Object channel : channels) {
        HashMap<Object, Object> channelMap = (HashMap<Object, Object>) channel;
        String label = (String) channelMap.get("label");
        if (label.equals(Constants.CHANNEL_LABEL)) {
          response = true;
        }
      }
    }
    return response;
  }

  /**
   * Check if the insights repo exists
   */
  @SuppressWarnings("unchecked")
  private boolean repoExists(String sessionKey) {
    Object[] repos = SatApi.listUserRepos(sessionKey);
    boolean exists = false;
    if (repos != null) {
      for (Object repo : repos) {
        HashMap<Object, Object> repoMap = (HashMap<Object, Object>) repo;
        String label = (String) repoMap.get("label"); 
        if (label.equals(Constants.REPO_LABEL)) {
          exists = true;
        }
      }
    }
    return exists;
  }

  /**
   * Create the repo via sat5 api if it doesn't already exist
   * Returns the new or existing repo id
   */
  private void createRepo(String sessionKey) {
    if (!repoExists(sessionKey)) {
      SatApi.createRepo(
          sessionKey, 
          Constants.REPO_LABEL, 
          "YUM", 
          Constants.REPO_URL);
    } 
  }

  /**
   * Subscribe a system to the insights software channel
   */
  @SuppressWarnings("unchecked")
  private boolean setSystemSoftwareChannels(
      String sessionKey,
      int systemId,
      boolean addInsightsChannel) {

    //list existing channels system is subscribed to
    Object[] systemChannels = SatApi.listSystemChannels(sessionKey, systemId);
    ArrayList<String> systemChannelLabels = new ArrayList<String>();
    if (systemChannels != null) {
      for (Object systemChannel : systemChannels) {
        String label = 
          (String)((HashMap<Object, Object>) systemChannel).get("label");
        //insights channel already associated
        if (label.equals(Constants.CHANNEL_LABEL)) {
          if (addInsightsChannel) {
            return true;
          }
        } else {
          systemChannelLabels.add(label);
        }
      }
    }
    if (addInsightsChannel) {
      systemChannelLabels.add(Constants.CHANNEL_LABEL);
    }

    //subscribe system to Red Hat Insights child channel
    int response = 
      SatApi.setChildChannels(sessionKey, systemId, systemChannelLabels);
    if (response != 1) {
      return false;
    } else {
      return true;
    }
  }

  /**
   * Create the insights custom software channel,
   * assocate to the insights repo,
   * syncronize the repo.
   */
  private boolean createChannel(String sessionKey) {
    boolean response = false;
    if (!channelExists(sessionKey)) {
      int created = SatApi.createChannel(
          sessionKey, 
          Constants.CHANNEL_LABEL,
          "x86_64 - Red Hat Insights",
          "Red Hat Insights is the coolest",
          "channel-x86_64",
          "rhel-x86_64-server-6");
      if (created == 0) {
        response = false;
      } else {
        response = true;
        //associate repo with this channel
        SatApi.associateRepo(sessionKey, Constants.CHANNEL_LABEL, Constants.REPO_LABEL);
        SatApi.syncRepo(sessionKey, Constants.CHANNEL_LABEL);
      }
    } else {
      //channel already created
      //TODO: verify the repo is associated?
      response = true;
    }
    return response;
  }

  /**
   * Create the insights config channel and add a default file
   */
  private void createConfigChannel(String sessionKey) {
    if (SatApi.configChannelExists(sessionKey, Constants.CONFIG_CHANNEL_LABEL) != 1) {
      SatApi.createConfigChannel(
          sessionKey, 
          Constants.CONFIG_CHANNEL_LABEL, 
          Constants.CONFIG_CHANNEL_NAME, 
          Constants.CONFIG_CHANNEL_DESCRIPTION);
      HashMap<String, Object> pathInfo = new  HashMap<String, Object>();
      pathInfo.put("contents", 
          "[redhat_access_proactive]" + 
          System.getProperty("line.separator") +
          "auto_config=true");
          //"# Change log level, valid options DEBUG, INFO, WARNING, ERROR, CRITICAL. Default DEBUG" +
          //System.getProperty("line.separator") +
          //"#loglevel=DEBUG" + 
          //System.getProperty("line.separator") +
          //"# Change authentication method, valid options BASIC, CERT. Default BASIC" +
          //System.getProperty("line.separator") +
          //"authmethod=BASIC" + 
          //System.getProperty("line.separator") +
          //"# URL to send uploads to" + 
          //System.getProperty("line.separator") +
          //"upload_url=https://sat57.usersys.redhat.com/redhataccess/rs/telemetry" + 
          //System.getProperty("line.separator") +
          //"# URL to send API requests to" + 
          //System.getProperty("line.separator") +
          //"api_url=https://sat57.usersys.redhat.com/redhataccess/rs/telemetry/api" + 
          //System.getProperty("line.separator") +
          //"username=" +
          //System.getProperty("line.separator") +
          //"password=");
      pathInfo.put("contents_enc64", false);
      pathInfo.put("owner", "root");
      pathInfo.put("group", "root");
      pathInfo.put("permissions", "644");
      pathInfo.put("binary", false);
      SatApi.configCreateOrUpdatePath(
          sessionKey,
          Constants.CONFIG_CHANNEL_LABEL,
          Constants.CONFIG_PATH,
          false,
          pathInfo);
    }
  }

  /**
   * Check if a user is the satellite administrator
   */
  private boolean userIsAdmin(String sessionKey, String username) {
    Object[] userRoles = SatApi.listUserRoles(sessionKey, username);
    boolean response = false;
    if (userRoles != null) {
      for (Object role : userRoles) {
        if (role.equals("satellite_admin")) {
          response = true;
        }
      }
    }
    return response;
  }
}
