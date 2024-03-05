package com.signomix.account.domain;

import java.util.List;

import org.jboss.logging.Logger;

import com.signomix.common.Tenant;
import com.signomix.common.User;
import com.signomix.common.db.IotDatabaseException;
import com.signomix.common.db.OrganizationDaoIface;

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
public class TenantLogic {

    @Inject
    Logger logger;
    
    @Inject
    @DataSource("oltp")
    AgroalDataSource tsDs;

    OrganizationDaoIface organiaztionDao;

    void onStart(@Observes StartupEvent ev) {
        organiaztionDao = new com.signomix.common.tsdb.OrganizationDao();
        organiaztionDao.setDatasource(tsDs);
    }

    public List<Tenant> getTenants(User user, Long organizationId, int limit, int offset) throws IotDatabaseException{
        if(user.organization.longValue()!=organizationId.longValue()){
            logger.error("User "+user.uid+" organization=="+user.organization+" tried to access organization "+organizationId);
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Not available" );
        }
        return organiaztionDao.getTenants(organizationId, limit, offset);
    }

    public Tenant getTenantById(User user, Integer tenantId) throws IotDatabaseException {
        Tenant tenant = organiaztionDao.getTenant(tenantId);
        if(tenant==null){
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Tenant not found" );
        }
        if(user.organization!=tenant.organizationId){
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Tenant not found" );
        }
        return tenant;
    }

    public Tenant getTenantByRoot(User user, Long organizationId, String root) throws IotDatabaseException {
        if(user.organization!=organizationId){
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Tenant not found" );
        }
        Tenant tenant = organiaztionDao.getTenantByRoot(user.organization, root);
        return tenant;
    }

    public void addTenant(User user, Tenant tenant) throws IotDatabaseException{
        if(user.organization!=tenant.organizationId){
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Not available" );
        }
        if(user.type!=User.MANAGING_ADMIN){
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Not available" );
        }
        organiaztionDao.addTenant(tenant.organizationId, tenant.name, tenant.root, tenant.menuDefinition);
    }

    public void updateTenant(User user, Tenant tenant)  throws IotDatabaseException{
        if(user.organization!=tenant.organizationId){
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Not available" );
        }
        Tenant original=organiaztionDao.getTenant(tenant.id);
        if(original==null){
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Not found" );
        }
        if(!organiaztionDao.canEditTenant(user, original)){
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Not available" );
        }
        organiaztionDao.updateTenant(tenant.id, tenant.organizationId, tenant.name, tenant.root, tenant.menuDefinition);
    }

    public void deleteTenant(User user, Integer tenantId) throws IotDatabaseException{
        Tenant original=organiaztionDao.getTenant(tenantId);
        if(original==null){
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Not found" );
        }
        if(user.organization!=original.organizationId){
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Not available" );
        }
        
        if(original==null){
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Not found" );
        }
        if(!organiaztionDao.canEditTenant(user, original)){
            throw new IotDatabaseException(IotDatabaseException.UNKNOWN, "Not available" );
        }
        organiaztionDao.deleteTenant(tenantId);
    }
}
