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

package org.alfasoftware.morf.upgrade.testhumanreadablestatements.upgrade.v1_0_0;

import org.alfasoftware.morf.sql.InsertStatement;
import org.alfasoftware.morf.sql.element.FieldLiteral;
import org.alfasoftware.morf.sql.element.TableReference;
import org.alfasoftware.morf.upgrade.DataEditor;
import org.alfasoftware.morf.upgrade.SchemaEditor;
import org.alfasoftware.morf.upgrade.Sequence;
import org.alfasoftware.morf.upgrade.UpgradeStep;

/**
 * A dummy upgrade step for a DSL based data change.
 *
 * @author Copyright (c) Alfa Financial Software 2010
 */
@Sequence(3)
public class AddDslDataChangeStep implements UpgradeStep {

  /**
   * @see org.alfasoftware.morf.upgrade.UpgradeStep#execute(org.alfasoftware.morf.upgrade.SchemaEditor, org.alfasoftware.morf.upgrade.DataEditor)
   */
  @Override
  public void execute(SchemaEditor schemaEditor, DataEditor data) {
    data.executeStatement(new InsertStatement().into(
      new TableReference("myTable")).values(
        new FieldLiteral("column1").as("wibble")));

  }

  /**
   * @see org.alfasoftware.morf.upgrade.UpgradeStep#getDescription()
   */
  @Override
  public String getDescription() {
    return "DSL data change";
  }

  /**
   * @see org.alfasoftware.morf.upgrade.UpgradeStep#getJiraId()
   */
  @Override
  public String getJiraId() {
    return "DAVEDEV-123";
  }
}
