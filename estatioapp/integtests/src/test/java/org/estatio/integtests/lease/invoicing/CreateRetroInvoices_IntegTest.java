/*
 *
 *  Copyright 2012-2014 Eurocommercial Properties NV
 *
 *
 *  Licensed under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.estatio.integtests.lease.invoicing;

import java.util.List;
import java.util.SortedSet;

import javax.inject.Inject;

import org.joda.time.LocalDate;
import org.junit.Before;
import org.junit.Test;

import org.apache.isis.applib.fixturescripts.FixtureScript;
import org.apache.isis.applib.services.factory.FactoryService;

import org.incode.module.base.integtests.VT;

import org.estatio.app.menus.invoice.InvoiceServiceMenuAndContributions;
import org.estatio.dom.asset.PropertyRepository;
import org.estatio.dom.invoice.Invoice;
import org.estatio.dom.invoice.InvoiceRepository;
import org.estatio.dom.invoice.InvoiceRunType;
import org.estatio.dom.lease.Lease;
import org.estatio.dom.lease.LeaseItem;
import org.estatio.dom.lease.LeaseItemType;
import org.estatio.dom.lease.LeaseRepository;
import org.estatio.dom.lease.LeaseTermForTurnoverRent;
import org.estatio.dom.lease.invoicing.InvoiceCalculationSelection;
import org.estatio.dom.lease.invoicing.InvoiceCalculationService;
import org.estatio.dom.lease.invoicing.InvoiceForLease;
import org.estatio.dom.lease.invoicing.InvoiceForLeaseRepository;
import org.estatio.fixture.EstatioBaseLineFixture;
import org.estatio.fixture.lease.LeaseForOxfTopModel001Gb;
import org.estatio.fixture.lease.LeaseItemAndTermsForOxfTopModel001;
import org.estatio.fixturescripts.CreateRetroInvoices;
import org.estatio.integtests.EstatioIntegrationTest;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class CreateRetroInvoices_IntegTest extends EstatioIntegrationTest {

    @Before
    public void setupData() {
        runFixtureScript(new FixtureScript() {
            @Override
            protected void execute(ExecutionContext executionContext) {
                executionContext.executeChild(this, new EstatioBaseLineFixture());

                executionContext.executeChild(this, new LeaseItemAndTermsForOxfTopModel001());
            }
        });
    }

    @Inject
    InvoiceRepository invoiceRepository;

    @Inject
    InvoiceForLeaseRepository invoiceForLeaseRepository;

    @Inject
    PropertyRepository propertyRepository;

    @Inject
    LeaseRepository leaseRepository;

    @Inject
    FactoryService factoryService;

    @Inject
    InvoiceCalculationService invoiceCalculationService;

    @Inject
    InvoiceServiceMenuAndContributions invoiceService;

    CreateRetroInvoices createRetroInvoices;

    Lease lease;

    @Before
    public void setup() {
        createRetroInvoices = new CreateRetroInvoices() {
            {
                leaseRepository = CreateRetroInvoices_IntegTest.this.leaseRepository;
                invoiceForLeaseRepository = CreateRetroInvoices_IntegTest.this.invoiceForLeaseRepository;
                invoiceRepository = CreateRetroInvoices_IntegTest.this.invoiceRepository;
                propertyRepository = CreateRetroInvoices_IntegTest.this.propertyRepository;
                invoiceCalculationService = CreateRetroInvoices_IntegTest.this.invoiceCalculationService;
                factoryService = CreateRetroInvoices_IntegTest.this.factoryService;
            }
        };

        lease = leaseRepository.findLeaseByReference(LeaseForOxfTopModel001Gb.REF);
    }

    public static class FindDueDatesForLease extends CreateRetroInvoices_IntegTest {

        @Test
        public void whenPresent() {
            // when
            SortedSet<LocalDate> dueDates = lease.dueDatesInRange(VT.ld(2012, 1, 1), VT.ld(2014, 1, 1));
            // then
            assertThat(dueDates.size(), is(10));
        }

    }

    public static class Terminate_and_Calculate extends CreateRetroInvoices_IntegTest {

        @Test
        public void step1_retroRun() {
            // given
            SortedSet<LocalDate> dueDates = lease.dueDatesInRange(VT.ld(2012, 1, 1), VT.ld(2014, 1, 1));
            assertThat(dueDates.size(), is(10));

            // when
            createRetroInvoices.createLease(lease, VT.ld(2012, 1, 1), VT.ld(2014, 1, 1), FixtureScript.ExecutionContext.NOOP);

            // then
            assertThat(invoiceForLeaseRepository.findByLease(lease).size(), is(10));

            // and given
            lease.terminate(VT.ld(2013, 10, 1));

            // when
            invoiceService.calculateLegacy(lease, InvoiceRunType.NORMAL_RUN, InvoiceCalculationSelection.ALL_RENT_AND_SERVICE_CHARGE, VT.ld(2014, 2, 1), VT.ld(2012, 1, 1), VT.ld(2014, 1, 1));

            // then
            List<InvoiceForLease> invoicesList = invoiceForLeaseRepository.findByLease(lease);
            assertThat(invoicesList.size(), is(11));
            Invoice invoice = invoicesList.get(10);
            assertThat(invoice.getDueDate(), is(VT.ld(2014, 2, 1)));
            assertThat(invoice.getGrossAmount(), is(VT.bd("-8170.01")));

            // and also
            LeaseItem leaseItem = lease.findFirstItemOfType(LeaseItemType.TURNOVER_RENT);
            LeaseTermForTurnoverRent term = (LeaseTermForTurnoverRent) leaseItem.findTerm(VT.ld(2012, 1, 1));
            assertThat(term.getContractualRent(), is(VT.bd("21058.27")));
        }
    }

}