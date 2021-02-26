package be.nabu.eai.module.services.crud.api;

import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebResult;

import be.nabu.eai.module.services.crud.CRUDFilter;
import be.nabu.eai.module.services.crud.ListResult;
import be.nabu.eai.module.services.crud.provider.CRUDMeta;

public interface CRUDProvider {
	
	// id is one of the filters (optionally)
	@WebResult(name = "results")
	public ListResult list(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			// the id of the type we are querying
			@WebParam(name = "typeId") String typeId,
			@WebParam(name = "limit") Integer limit, 
			@WebParam(name = "offset") Long offset, 
			@WebParam(name = "orderBy") List<String> orderBy, 
			@WebParam(name = "filters") List<CRUDFilter> filters,
			@WebParam(name = "language") String language,
			// it is unclear if we want generic configuration at this point 
			// and even if we have generic configuration, if this belongs to provider-specific configuration or rather all providers?
			// if it is provider-specific, the REST endpoints won't know what the parameters do, making it hard to force this to true
			// this particular parameter is likely relevant enough to promote to a dedicated parameter rather than a generic one
			@WebParam(name = "limitToUser") Boolean limitToUser,
			// some generic metadata we have about the crud service
			@WebParam(name = "meta") CRUDMeta meta);
	
	public void create(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			@WebParam(name = "instance") Object object,
			@WebParam(name = "language") String language,
			@WebParam(name = "changeTracker") String changeTracker,
			@WebParam(name = "typeId") String typeId,
			@WebParam(name = "meta") CRUDMeta meta);
	
	public void update(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			@WebParam(name = "instance") Object object,
			@WebParam(name = "language") String language,
			@WebParam(name = "changeTracker") String changeTracker,
			@WebParam(name = "typeId") String typeId,
			@WebParam(name = "meta") CRUDMeta meta);
	
	public void delete(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			@WebParam(name = "typeId") String typeId,
			@WebParam(name = "id") Object id,
			@WebParam(name = "language") String language,
			@WebParam(name = "changeTracker") String changeTracker,
			@WebParam(name = "meta") CRUDMeta meta);

}
