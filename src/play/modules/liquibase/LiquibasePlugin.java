package play.modules.liquibase;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import liquibase.Liquibase;
import liquibase.changelog.ChangeSet;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.DatabaseException;
import liquibase.exception.LiquibaseException;
import liquibase.resource.ClassLoaderResourceAccessor;
import play.Logger;
import play.Play;
import play.PlayPlugin;
import play.db.DB;
import play.utils.Properties;

public class LiquibasePlugin extends PlayPlugin {

	@Override
	public void onApplicationStart() {
		
		String autoupdate = Play.configuration.getProperty("liquibase.active");
		String mainchangelogpath = Play.configuration.getProperty("liquibase.changelog", "mainchangelog.xml");
		String propertiespath = Play.configuration.getProperty("liquibase.properties", "liquibase.properties");
		String contexts = Play.configuration.getProperty("liquibase.contexts");
		Database db = null;
		
		
		if (null != autoupdate && "true".equals(autoupdate)) {
			Logger.info("Auto update flag found and positive => let's get on with changelog update");
			try {
				
				Connection cnx = DB.datasource.getConnection();
				cnx.setAutoCommit(false);
				
				Liquibase liquibase = new Liquibase(mainchangelogpath, new ClassLoaderResourceAccessor(), new JdbcConnection(cnx));
				InputStream stream = Play.classloader.getResourceAsStream(propertiespath);

				if (null != stream) {
					Properties props = new Properties();
					props.load(stream);
					
					for (String key:props.keySet()) {
						String val = props.get(key);
						Logger.info("found parameter [%1$s] / [%2$s] for liquibase update", key, val);
						liquibase.setChangeLogParameter(key, val);
					}
				} else {
					Logger.warn("Could not find properties file [%s]", propertiespath);
				}
				
				Logger.info("Ready for database diff generation");
				List<ChangeSet> unruns = liquibase.listUnrunChangeSets(contexts);
				Logger.info("Unrun changesets count [%s]", unruns.size());
				Logger.debug("Unrun changesets [%s]", unruns);
				db = liquibase.getDatabase();
				liquibase.update(contexts);
				Logger.info("Changelog Execution performed");
				
			} catch (SQLException sqle) {
				throw new LiquibaseUpdateException(sqle.getMessage());
			} catch (LiquibaseException e) { 
				throw new LiquibaseUpdateException(e.getMessage());
			} catch (IOException ioe) {
				throw new LiquibaseUpdateException(ioe.getMessage());				
			} finally {
				if (null != db) {
					try {
						db.rollback();
						db.close();
					} catch (DatabaseException e) {
						Logger.warn(e,"problem closing connection");
					}
				}
			}

		} else {
			Logger.info("Auto update flag set to false or not available => skipping structural update");
		}
	}
}
