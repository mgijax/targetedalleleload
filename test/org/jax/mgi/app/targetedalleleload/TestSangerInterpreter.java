package org.jax.mgi.app.targetedalleleload;

import java.util.ArrayList;
import java.util.List;

import org.jax.mgi.app.targetedalleleload.KnockoutAlleleInterpreter;
import org.jax.mgi.app.targetedalleleload.SangerInterpreter;
import org.jax.mgi.shr.exception.MGIException;

import junit.framework.TestCase;

public class TestSangerInterpreter extends TestCase {

	public String [] multiInput = {
			"MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	conditional_ready	90879213	90879083	90878296	90878244",
			"MGI:98809	NCBIM37	L1L2_gt0	EUCOMM	72415	EPD0001_3_G07	SI2.3	tm1a(EUCOMM)Wtsi	conditional_ready	66884542	66884386	66883463	66883459",
			"MGI:1278322	NCBIM37	L1L2_gt0	EUCOMM	72417	EPD0002_3_G03	SI2.3	tm1a(EUCOMM)Wtsi	conditional_ready	6463514 6463421 6462618 6462565",
			"MGI:2150037	NCBIM37	L1L2_gt1	EUCOMM	72418	EPD0002_2_B07	SI2.3	tm1a(EUCOMM)Wtsi	conditional_ready	114349871	114349929	114350602	114350773",
			"MGI:1919963	NCBIM37	L1L2_Pgk_P	EUCOMM	83916	EPD0559_2_H12	JM8A3.N1 p10	tm2e(EUCOMM)Wtsi	targeted_non_conditional	158773891	158773910	        ",
			"MGI:1919963	NCBIM37	L1L2_Pgk_P	EUCOMM	83916	EPD0559_2_H10	JM8A3.N1 p10	tm2e(EUCOMM)Wtsi	targeted_non_conditional	158773891	158773910               ",
			"MGI:1919963	NCBIM37	L1L2_Pgk_P	EUCOMM	83916	EPD0559_2_H10	JM8A3.N1 p10	tm2e(EUCOMM)Wtsi	targeted_non_conditional	158773891	158773910       "
	};

	public String validInput1 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	conditional_ready	90879213	90879083	90878296	90878244";
	public String validInput2 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	conditional_ready	90879083	90879213	90878244	90878296";
	public String validInput3 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	deletion	90879213	90879083		";
	public String validInput4 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	targeted_non_conditional	90879213	90879083	90878296	90878244";

	public String inValidInput1 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416		SI2.3	tm2a(EUCOMM)Wtsi	targeted_non_conditional	90879213	90879083	90878296	90878244";
	public String inValidInput2 = "	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	targeted_non_conditional	90879213	90879083	90878296	90878244";
	public String inValidInput3 = "MGI:1349766	NCBIM37	L1L2_gt2	KOMP-CSD	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	targeted_non_conditional	90879213	90879083	90878296	90878244";
	public String inValidInput4 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	DEPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	targeted_non_conditional	90879213	90879083	90878296	90878244";
	public String inValidInput5 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	BAD_ALLELE_TYPE	90879213	90879083	90878296	90878244";
	public String inValidInput6 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi		90879213	90879083	90878296	90878244";
	public String inValidInput7 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	targeted_non_conditional	a	90879083	90878296	90878244";
	public String inValidInput8 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	targeted_non_conditional	90879213	b	90878296	90878244";
	public String inValidInput9 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	targeted_non_conditional		90879083	90878296	90878244";
	public String inValidInput10 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	targeted_non_conditional	90879213	90879083		90878244";
	public String inValidInput11 = "MGI:1349766	NCBIM37	L1L2_gt2	EUCOMM	72416	EPD0001_3_E04	SI2.3	tm2a(EUCOMM)Wtsi	targeted_non_conditional	90879213	90879083	90878296	";

	public void testInterpret() throws MGIException{
		
		List allowedCelllines = new ArrayList();
		allowedCelllines.add("E");
		List knownCelllines = new ArrayList();
		knownCelllines.add("E");
		knownCelllines.add("H");
		knownCelllines.add("D");

		KnockoutAlleleInterpreter kai = new SangerInterpreter(allowedCelllines, 
				knownCelllines, 
				"EUCOMM");

		SangerAlleleInput check = (SangerAlleleInput) kai.interpret(validInput1);

		SangerAlleleInput good = new SangerAlleleInput();
		good.setESCellName("EPD0001_3_E04");
		good.setMutationType("Conditional");
		good.setParentESCellName("SI2.3");
		good.setProjectId("72416");
		good.setGeneId("MGI:1349766");
		good.setLocus1("90879213");
		good.setLocus2("90878244");
		good.setBuild("37");
		good.setCassette("L1L2_gt2");
		good.setInputPipeline("EUCOMM");
		System.out.print(good);
		System.out.print("\n");
		System.out.print(check);
		assertTrue(check.equals(good));
	}

	public void testIsValid() {

		KnockoutAlleleInterpreter kai =  null;
		List allowedCelllines = new ArrayList();
		allowedCelllines.add("E");
		List knownCelllines = new ArrayList();
		knownCelllines.add("E");
		knownCelllines.add("H");
		knownCelllines.add("D");

		try {
			kai = new SangerInterpreter(allowedCelllines, 
					knownCelllines, 
					"EUCOMM");
		} catch (MGIException e) {
			e.printStackTrace();
			fail("Instantiation error");
		}
		
		if (kai == null) {
			fail("Could not create new instance of SangerInterpreter");
		}
		
		List validInputs = new ArrayList();
		validInputs.add(validInput1);
		validInputs.add(validInput2);
		validInputs.add(validInput3);
		validInputs.add(validInput4);

		for (int i = 0; i<validInputs.size(); i++) {
			String input = (String)validInputs.get(i);
			assertTrue(kai.isValid(input));			
		}
		
		List inValidInputs = new ArrayList();
		inValidInputs.add(inValidInput1);
		inValidInputs.add(inValidInput2);
		inValidInputs.add(inValidInput3);
		inValidInputs.add(inValidInput4);
		inValidInputs.add(inValidInput5);
		inValidInputs.add(inValidInput6);
		inValidInputs.add(inValidInput7);
		inValidInputs.add(inValidInput8);
		inValidInputs.add(inValidInput9);
		inValidInputs.add(inValidInput10);
		inValidInputs.add(inValidInput11);

		for (int i = 0; i<inValidInputs.size(); i++) {
			String input = (String)inValidInputs.get(i);
			assertFalse(kai.isValid(input));
		}
	}
}
