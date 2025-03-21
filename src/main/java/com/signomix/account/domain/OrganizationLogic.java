package com.signomix.account.domain;

import java.util.List;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import com.signomix.account.exception.ServiceException;
import com.signomix.common.Organization;
import com.signomix.common.User;
import com.signomix.common.db.IotDatabaseException;
import com.signomix.common.db.OrganizationDaoIface;
import com.signomix.common.db.UserDao;
import com.signomix.common.db.UserDaoIface;
import com.signomix.common.gui.Dashboard;
import com.signomix.common.iot.Device;
import com.signomix.common.iot.DeviceGroup;

import io.agroal.api.AgroalDataSource;
import io.quarkus.agroal.DataSource;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Klasa zawierająca logikę biznesową dotyczącą autoryzacji.
 * 
 * @author Grzegorz
 */
@ApplicationScoped
public class OrganizationLogic {
    @Inject
    Logger logger;

    @Inject
    @DataSource("user")
    AgroalDataSource userDataSource;

    UserDaoIface userDao;
    OrganizationDaoIface organizationDao;

    @ConfigProperty(name = "signomix.exception.api.unauthorized")
    String userNotAuthorizedException;

    @ConfigProperty(name = "signomix.database.type")
    String databaseType;

    private long defaultOrganizationId = 0;

    void onStart(@Observes StartupEvent ev) {
        if ("h2".equalsIgnoreCase(databaseType)) {
            userDao = new UserDao();
            userDao.setDatasource(userDataSource);
            //iotDao = new IotDatabaseDao();
            //iotDao.setDatasource(deviceDataSource);
            defaultOrganizationId = 0;
        } else if ("postgresql".equalsIgnoreCase(databaseType)) {
            userDao = new com.signomix.common.tsdb.UserDao();
            userDao.setDatasource(userDataSource);
            //iotDao = new com.signomix.common.tsdb.IotDatabaseDao();
            //iotDao.setDatasource(tsDs);
            organizationDao = new com.signomix.common.tsdb.OrganizationDao();
            organizationDao.setDatasource(userDataSource);
            defaultOrganizationId = 1;
        } else {
            logger.error("Unknown database type: " + databaseType);
        }

/*         try {
            defaultOrganizationId = iotDao.getParameterValue("system.default.organization", User.ANY);
        } catch (IotDatabaseException e) {
            logger.error("Unable to get default organization id: " + e.getMessage());
        } */
    }


    public List<Organization> getOrganizations(Integer limit, Integer offset) throws ServiceException {
        try {
            return organizationDao.getOrganizations(limit, offset);
        } catch (IotDatabaseException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    public Organization getOrganization(User user, long organizationId) throws ServiceException {
        try {
            return organizationDao.getOrganization(organizationId);
        } catch (IotDatabaseException e) {
            e.printStackTrace();
            throw new ServiceException(e.getMessage());
        }
    }

    public List<Organization> getOrganizations(User user, Integer limit, Integer offset) throws ServiceException {
        try {
            return organizationDao.getOrganizations(limit, offset);
        } catch (IotDatabaseException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    public void addOrganization(User user, Organization organization) throws ServiceException {
        try {
            organizationDao.addOrganization(organization);
        } catch (IotDatabaseException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    public void updateOrganization(User user, Organization organization) throws ServiceException {
        try {
            organizationDao.updateOrganization(organization);
        } catch (IotDatabaseException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    public void deleteOrganization(User user, long organizationId) throws ServiceException {
        try {
            organizationDao.deleteOrganization(organizationId);
        } catch (IotDatabaseException e) {
            throw new ServiceException(e.getMessage());
        }
    }

    /**
     * Checks if user is system admin
     * 
     * @param user
     * @return
     */
    public boolean isSystemAdmin(User user) {
        return user != null && user.type == User.OWNER;
    }

    /**
     * Checks if user is organization admin
     * 
     * @param user
     * @param organizationId
     * @return
     */
    public boolean isOrganizationAdmin(User user, long organizationId) {
        return user != null && user.organization == organizationId && user.type == User.ADMIN;
    }

    /**
     * Checks if user is organization user
     * 
     * @param user
     * @param organizationId
     * @return
     */
    public boolean isOrganizationMember(User user, long organizationId) {
        logger.info("user " + user);
        logger.info("user.organization: " + user.organization);
        logger.info("organizationId: " + organizationId);
        return user != null && user.organization == organizationId;
    }

    /**
     * Checks user access to object (Device, Dashboard)
     * 
     * @param user
     * @param writeAccess
     * @param defaultOrganizationId
     * @param accessedObject
     * @return
     */
    public boolean hasObjectAccess(
            User user,
            boolean writeAccess,
            long defaultOrganizationId,
            Object accessedObject) {
        // Platfor administrator has access to all objects
        if (user.type == User.OWNER) {
            return true;
        }
        String owner = null;
        String team = null;
        String admins = null;
        long organizationId = 0;
        if (accessedObject instanceof Dashboard) {
            Dashboard dashboard = (Dashboard) accessedObject;
            team = dashboard.getTeam();
            admins = dashboard.getAdministrators();
            owner = dashboard.getUserID();
            organizationId = dashboard.getOrganizationId();
        } else if (accessedObject instanceof Device) {
            Device device = (Device) accessedObject;
            team = device.getTeam();
            admins = device.getAdministrators();
            owner = device.getUserID();
            organizationId = device.getOrganizationId();
        } else if (accessedObject instanceof DeviceGroup) {
            DeviceGroup group = (DeviceGroup)accessedObject;
            team = group.getTeam();
            admins = group.getAdministrators();
            owner = group.getUserID();
            organizationId = group.getOrganization();
        } else {
            logger.error("Unknown object type: " + accessedObject.getClass().getName());
            return false;
        }

        // object owner has read/write access
        if (owner.equals(user.uid))
            return true;
        // access depands on organization
        if (user.organization == defaultOrganizationId) {
            if (admins.contains("," + user.uid + ","))
                return true;
            if (!writeAccess) {
                if (team.contains("," + user.uid + ","))
                    return true;
            }
        } else {
            if (!writeAccess) {
                if (user.organization == organizationId)
                    return true;
            } else {
                if (user.organization == organizationId && user.type == User.ADMIN) {
                    return true;
                } else {
                    return false;
                }
            }
        }
        return false;
    }
}
