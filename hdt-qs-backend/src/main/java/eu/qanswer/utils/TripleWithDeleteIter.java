package eu.qanswer.utils;

import eu.qanswer.hybridstore.HybridTripleSource;
import eu.qanswer.model.HDTStatement;

import org.eclipse.rdf4j.common.iteration.CloseableIteration;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.sail.SailException;
import org.rdfhdt.hdt.hdt.HDT;
import org.rdfhdt.hdt.triples.IteratorTripleID;
import org.rdfhdt.hdt.triples.TripleID;

import java.util.Iterator;

public class TripleWithDeleteIter implements Iterator<Statement> {

    private HybridTripleSource tripleSource;
    private IteratorTripleID iterator;
    private HDT hdt;
    private CloseableIteration<? extends Statement, SailException> repositoryResult;

    private IRIConverter iriConverter;

    public TripleWithDeleteIter(HybridTripleSource tripleSource, IteratorTripleID iter) {
        this.tripleSource = tripleSource;
        this.iterator = iter;
        this.hdt = tripleSource.getHdt();
    }

    public TripleWithDeleteIter(HybridTripleSource tripleSource, IteratorTripleID iter,
                                CloseableIteration<? extends Statement,
                                        SailException> repositoryResult
    ) {
        this.tripleSource = tripleSource;
        this.iterator = iter;
        this.hdt = tripleSource.getHdt();
        this.repositoryResult = repositoryResult;
        this.iriConverter = new IRIConverter(hdt);
    }

    Statement next;

    @Override
    public boolean hasNext() {
        // iterate over the result of hdt
        if (iterator != null) {
            while (iterator.hasNext()) {
                TripleID tripleID = iterator.next();
                Statement stm = new HDTStatement(hdt, tripleID, tripleSource);
                if (tripleID.getIndex() != -1 && !tripleSource.getHybridStore().getDeleteBitMap().access(tripleID.getIndex() - 1)) {
                    next = stm;
                    return true;
                }
            }
        }
        // iterate over the result of rdf4j
        if (this.repositoryResult != null && this.repositoryResult.hasNext()) {
            Statement stm = repositoryResult.next();
            next = convertStatement(stm);
            return true;
        }
        return false;
    }

    private Statement convertStatement(Statement stm) {

        Resource subject = stm.getSubject();
        Resource newSubj = iriConverter.getIRIHdtSubj(subject);
        IRI predicate = stm.getPredicate();
        Value newPred = iriConverter.getIRIHdtPred(predicate);
//        if(newPred instanceof SimpleIRIHDT && ((SimpleIRIHDT)newPred).getId() == -1){
//            System.out.println("alerttttt this should not happen: "+newPred.toString());
//        }
        Value newObject = iriConverter.getIRIHdtObj(stm.getObject());
        return this.tripleSource.getValueFactory().createStatement(newSubj, (IRI) newPred, newObject, stm.getContext());


    }

    @Override
    public Statement next() {
        Statement stm = this.tripleSource.getValueFactory().createStatement(next.getSubject(), next.getPredicate(), next.getObject(), next.getContext());
        return stm;
    }
}
