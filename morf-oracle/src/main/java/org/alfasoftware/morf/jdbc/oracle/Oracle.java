/* Copyright 2017 Alfa Financial Software
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.alfasoftware.morf.jdbc.oracle;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLTransientException;
import java.util.Optional;
import java.util.Stack;

import javax.sql.XADataSource;

import org.alfasoftware.morf.jdbc.AbstractDatabaseType;
import org.alfasoftware.morf.jdbc.JdbcUrlElements;
import org.alfasoftware.morf.jdbc.SqlDialect;
import org.alfasoftware.morf.metadata.Schema;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;


/**
 * Support for Oracle database hosts.
 *
 * @author Copyright (c) Alfa Financial Software 2017
 */
public final class Oracle extends AbstractDatabaseType {

  private static final Log log = LogFactory.getLog(Oracle.class);

  public static final String IDENTIFIER = "ORACLE";


  /**
   * Constructor.
   */
  public Oracle() {
    super("oracle.jdbc.driver.OracleDriver", IDENTIFIER);
  }


  /**
   *
   * @see org.alfasoftware.morf.jdbc.DatabaseType#formatJdbcUrl(org.alfasoftware.morf.jdbc.JdbcUrlElements)
   */
  @Override
  public String formatJdbcUrl(JdbcUrlElements jdbcUrlElements) {
    return "jdbc:oracle:thin:@" + jdbcUrlElements.getHostName() + (jdbcUrlElements.getPort() == 0 ? "" : ":" + jdbcUrlElements.getPort()) + "/" + jdbcUrlElements.getInstanceName();
  }

  /**
   *  @see org.alfasoftware.morf.jdbc.DatabaseType#openSchema(Connection, String, String)
   */
  @Override
  public Schema openSchema(Connection connection, String databaseName, String schemaName) {
    if (StringUtils.isEmpty(schemaName)) throw new IllegalStateException("No schema name has been provided, but a schema name is required when connecting to Oracle");
    return new OracleMetaDataProvider(connection, schemaName);
  }


  /**
   * @see org.alfasoftware.morf.jdbc.DatabaseType#canTrace()
   */
  @Override
  public boolean canTrace() {
    return true;
  }


  /**
   * Returns an Oracle XA data source. Note that this method may fail at
   * run-time if {@code OracleXADataSource} is not available on the classpath.
   *
   * @throws IllegalStateException If the data source cannot be created.
   *
   * @see org.alfasoftware.morf.jdbc.DatabaseType#getXADataSource(java.lang.String,
   *      java.lang.String, java.lang.String)
   */
  @Override
  public XADataSource getXADataSource(String jdbcUrl, String username, String password) {
    try {
      log.info("Initialising Oracle XA data source...");
      XADataSource dataSource = (XADataSource) Class.forName("oracle.jdbc.xa.client.OracleXADataSource").newInstance();
      dataSource.getClass().getMethod("setURL", String.class).invoke(dataSource, jdbcUrl);
      dataSource.getClass().getMethod("setUser", String.class).invoke(dataSource, username);
      dataSource.getClass().getMethod("setPassword", String.class).invoke(dataSource, password);
      return dataSource;
    } catch (Exception e) {
      throw new IllegalStateException("Failed to create Oracle XA data source", e);
    }
  }


  /**
   * @see org.alfasoftware.morf.jdbc.DatabaseType#sqlDialect(java.lang.String)
   */
  @Override
  public SqlDialect sqlDialect(String schemaName) {
    return new OracleDialect(schemaName);
  }


  /**
   * @see org.alfasoftware.morf.jdbc.DatabaseType#matchesProduct(java.lang.String)
   */
  @Override
  public boolean matchesProduct(String product) {
    return product.equalsIgnoreCase("Oracle");
  }


  /**
   * @see org.alfasoftware.morf.jdbc.DatabaseType#reclassifyException(java.lang.Exception)
   */
  @Override
  public Exception reclassifyException(Exception e) {
    // Reclassify OracleXA exceptions
    Optional<Integer> xaErrorCode = getErrorCodeFromOracleXAException(e);
    if (xaErrorCode.isPresent()) {
      // ORA-00060: Deadlock detected while waiting for resource
      // ORA-02049: Distributed transaction waiting for lock
      if (xaErrorCode.get() == 60 || xaErrorCode.get() == 2049) {
        return new SQLTransientException(e.getMessage(), null, xaErrorCode.get(), e);
      }
      return new SQLException(e.getMessage(), null, xaErrorCode.get(), e);
    }

    // Reclassify any SQLExceptions which should be SQLTransientExceptions but are not. Specifically this handles BatchUpdateExceptions
    if(e instanceof SQLException && !(e instanceof SQLTransientException)) {
      int errorCode = ((SQLException) e).getErrorCode();
      if(errorCode == 60 || errorCode == 2049) {
        return new SQLTransientException(e.getMessage(), ((SQLException) e).getSQLState(), errorCode, e);
      }
    }

    return e;
  }


  /**
   * Recursively try and extract the error code from any nested OracleXAException
   */
  private Optional<Integer> getErrorCodeFromOracleXAException(Throwable exception) {
    try {
      if ("oracle.jdbc.xa.OracleXAException".equals(exception.getClass().getName())) {
        return Optional.of((Integer) exception.getClass().getMethod("getOracleError").invoke(exception));
      } else if (exception.getCause() != null) {
        return getErrorCodeFromOracleXAException(exception.getCause());
      }
      return Optional.empty();
    } catch (Exception e) {
      log.error("Exception when trying to extract error code", exception);
      throw new RuntimeException(e);
    }
  }


  /**
   *
   * @see org.alfasoftware.morf.jdbc.AbstractDatabaseType#extractJdbcUrl(java.lang.String)
   */
  @Override
  public Optional<JdbcUrlElements> extractJdbcUrl(String jdbcUrl) {
    Stack<String> splitURL = splitJdbcUrl(jdbcUrl);

    String scheme = splitURL.pop();

    if (!scheme.equalsIgnoreCase("oracle")) {
      return Optional.empty();
    }

    splitURL.pop(); // Remove the "mem" or "thin"
    splitURL.pop(); // Remove the delimiter

    if (!splitURL.pop().equals(":@")) {
      throw new IllegalArgumentException("Expected '@' to follow the scheme name in [" + jdbcUrl + "]");
    }

    JdbcUrlElements.Builder connectionDetails = extractHostAndPort(splitURL);

    // Now get the path
    String path = extractPath(splitURL);

    connectionDetails.withInstanceName(path);

    return Optional.of(connectionDetails.build());
  }
}