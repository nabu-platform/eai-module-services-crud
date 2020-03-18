package be.nabu.eai.module.services.crud.api;

import java.util.List;

import javax.jws.WebParam;
import javax.jws.WebResult;

import be.nabu.eai.module.services.crud.CRUDFilter;
import be.nabu.eai.module.services.crud.ListResult;

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
			@WebParam(name = "language") String language);
	
	public void create(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			@WebParam(name = "instance") Object object,
			@WebParam(name = "language") String language,
			@WebParam(name = "changeTracker") String changeTracker,
			@WebParam(name = "typeId") String typeId);
	
	public void update(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			@WebParam(name = "instance") Object object,
			@WebParam(name = "language") String language,
			@WebParam(name = "changeTracker") String changeTracker,
			@WebParam(name = "typeId") String typeId);
	
	public void delete(@WebParam(name = "connectionId") String connectionId,
			@WebParam(name = "transactionId") String transactionId,
			@WebParam(name = "typeId") String typeId,
			@WebParam(name = "id") Object id,
			@WebParam(name = "language") String language,
			@WebParam(name = "changeTracker") String changeTracker);

}
