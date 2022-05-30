package com.the_qa_company.qendpoint.compiler;

import com.github.jsonldjava.shaded.com.google.common.base.Stopwatch;
import com.the_qa_company.qendpoint.utils.FormatUtils;
import com.the_qa_company.qendpoint.utils.rdf.BooleanQueryResult;
import com.the_qa_company.qendpoint.utils.rdf.QueryResultCounter;
import com.the_qa_company.qendpoint.utils.rdf.RDFHandlerCounter;
import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import org.eclipse.rdf4j.query.BooleanQuery;
import org.eclipse.rdf4j.query.GraphQuery;
import org.eclipse.rdf4j.query.QueryEvaluationException;
import org.eclipse.rdf4j.query.QueryLanguage;
import org.eclipse.rdf4j.query.QueryResult;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.TupleQueryResultHandler;
import org.eclipse.rdf4j.query.Update;
import org.eclipse.rdf4j.query.explanation.Explanation;
import org.eclipse.rdf4j.query.parser.ParsedBooleanQuery;
import org.eclipse.rdf4j.query.parser.ParsedGraphQuery;
import org.eclipse.rdf4j.query.parser.ParsedQuery;
import org.eclipse.rdf4j.query.parser.ParsedTupleQuery;
import org.eclipse.rdf4j.query.parser.QueryParserUtil;
import org.eclipse.rdf4j.query.resultio.QueryResultFormat;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriter;
import org.eclipse.rdf4j.query.resultio.TupleQueryResultWriterRegistry;
import org.eclipse.rdf4j.repository.RepositoryConnection;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.eclipse.rdf4j.repository.sail.SailRepository;
import org.eclipse.rdf4j.repository.sail.SailRepositoryConnection;
import org.eclipse.rdf4j.rio.ParserConfig;
import org.eclipse.rdf4j.rio.RDFFormat;
import org.eclipse.rdf4j.rio.RDFHandler;
import org.eclipse.rdf4j.rio.Rio;
import org.eclipse.rdf4j.rio.helpers.BasicParserSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ServerWebInputException;

import java.io.OutputStream;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Pattern;

/**
 * Repository to help using SPARQL queries on a sail repository
 *
 * @author Antoine Willerval
 */
public class SparqlRepository {
	private static final Logger logger = LoggerFactory.getLogger(SparqlRepository.class);
	private String sparqlPrefixes = "";
	private final CompiledSail compiledSail;
	private final SailRepository repository;

	public SparqlRepository(CompiledSail compiledSail) {
		this.compiledSail = Objects.requireNonNull(compiledSail, "compiledSail can't be null!");
		this.repository = new SailRepository(compiledSail);
	}

	/**
	 * @return the wrapped repository
	 */
	public SailRepository getRepository() {
		return repository;
	}

	/**
	 * Shutdown the repository
	 */
	public void shutDown() {
		repository.shutDown();
	}

	/**
	 * Init the repository
	 */
	public void init() {
		repository.init();
	}

	/**
	 * @return a connection to this repository
	 * @throws RepositoryException any exception returned by
	 *                             {@link org.eclipse.rdf4j.repository.sail.SailRepository#getConnection()}
	 */
	public SailRepositoryConnection getConnection() throws RepositoryException {
		return repository.getConnection();
	}

	/**
	 * @return the options of the compiled sail
	 */
	public CompiledSailOptions getOptions() {
		return compiledSail.getOptions();
	}

	/**
	 * reindex all the lucene sails of this repository
	 *
	 * @throws Exception any exception returned by
	 *                   {@link org.eclipse.rdf4j.sail.lucene.LuceneSail#reindex()}
	 */
	public void reindexLuceneSails() throws Exception {
		compiledSail.reindexLuceneSails();
	}

	/**
	 * set the sparql prefixes to put before queries
	 *
	 * @param sparqlPrefixes the prefixes
	 */
	public void setSparqlPrefixes(String sparqlPrefixes) {
		this.sparqlPrefixes = sparqlPrefixes;
	}

	/**
	 * execute a sparql query
	 *
	 * @param sparqlQuery  the query
	 * @param timeout      query timeout
	 * @param acceptHeader accept header (useless if out is null)
	 * @param mimeSetter   mime setter (useless if out is null)
	 * @param out          output stream
	 * @return query result if the output stream is null (useless if out isn't
	 *         null), return
	 *         {@link com.the_qa_company.qendpoint.utils.rdf.BooleanQueryResult}
	 *         for boolean queries
	 */
	public QueryResult<?> execute(String sparqlQuery, int timeout, String acceptHeader, Consumer<String> mimeSetter,
			OutputStream out) {
		try (RepositoryConnection connection = repository.getConnection()) {
			sparqlQuery = sparqlQuery.replaceAll("MINUS \\{(.*\\n)+.+}\\n\\s+}", "");
			// sparqlQuery = sparqlPrefixes+sparqlQuery;

			logger.info("Running given sparql query: {}", sparqlQuery);

			ParsedQuery parsedQuery = QueryParserUtil.parseQuery(QueryLanguage.SPARQL, sparqlQuery, null);

			if (compiledSail.getOptions().isDebugShowPlans()) {
				System.out.println(parsedQuery);
			}

			if (parsedQuery instanceof ParsedTupleQuery) {
				TupleQuery query = connection.prepareTupleQuery(sparqlQuery);
				query.setMaxExecutionTime(timeout);
				boolean error = false;
				try {
					if (out != null) {
						QueryResultFormat format = FormatUtils.getResultWriterFormat(acceptHeader).orElseThrow(
								() -> new ServerWebInputException("accept formats not supported: " + acceptHeader));
						mimeSetter.accept(format.getDefaultMIMEType());
						TupleQueryResultHandler writer = TupleQueryResultWriterRegistry.getInstance().get(format)
								.orElseThrow().getWriter(out);
						if (compiledSail.getOptions().isDebugShowCount()) {
							writer = new QueryResultCounter(writer);
						}
						query.evaluate(writer);
						if (compiledSail.getOptions().isDebugShowCount()) {
							logger.info("Complete query with {} triples", ((QueryResultCounter) writer).getCount());
						}
						return null;
					} else {
						return query.evaluate();
					}
				} catch (QueryEvaluationException q) {
					error = true;
					logger.error("This exception was caught [" + q + "]");
					q.printStackTrace();
					throw new RuntimeException(q);
				} finally {
					if (!error && compiledSail.getOptions().isDebugShowTime()) {
						System.out.println(query.explain(Explanation.Level.Timed));
					}
				}
			} else if (parsedQuery instanceof ParsedBooleanQuery) {
				BooleanQuery query = connection.prepareBooleanQuery(sparqlQuery);
				query.setMaxExecutionTime(timeout);
				if (out != null) {
					QueryResultFormat format = FormatUtils.getResultWriterFormat(acceptHeader).orElseThrow(
							() -> new ServerWebInputException("accept formats not supported: " + acceptHeader));
					mimeSetter.accept(format.getDefaultMIMEType());
					TupleQueryResultWriter writer = TupleQueryResultWriterRegistry.getInstance().get(format)
							.orElseThrow().getWriter(out);
					writer.handleBoolean(query.evaluate());
					return null;
				} else {
					return new BooleanQueryResult(query.evaluate());
				}
			} else if (parsedQuery instanceof ParsedGraphQuery) {
				GraphQuery query = connection.prepareGraphQuery(sparqlQuery);
				try {
					if (out != null) {
						RDFFormat format = FormatUtils.getRDFWriterFormat(acceptHeader).orElseThrow(
								() -> new ServerWebInputException("accept formats not supported: " + acceptHeader));
						mimeSetter.accept(format.getDefaultMIMEType());
						RDFHandler handler = Rio.createWriter(format, out);
						if (compiledSail.getOptions().isDebugShowCount()) {
							handler = new RDFHandlerCounter(handler);
						}
						query.evaluate(handler);
						if (compiledSail.getOptions().isDebugShowCount()) {
							logger.info("Complete query with {} triples", ((RDFHandlerCounter) handler).getCount());
						}
						return null;
					} else {
						return query.evaluate();
					}
				} catch (QueryEvaluationException q) {
					logger.error("This exception was caught [" + q + "]");
					q.printStackTrace();
					throw new RuntimeException(q);
				}
			} else {
				throw new ServerWebInputException("query not supported");
			}
		}
	}

	/**
	 * execute a sparql update query
	 *
	 * @param sparqlQuery the query
	 * @param timeout     query timeout
	 * @param out         the output stream, can be null
	 */
	public void executeUpdate(String sparqlQuery, int timeout, OutputStream out) {
		// logger.info("Running update query:"+sparqlQuery);
		sparqlQuery = sparqlPrefixes + sparqlQuery;
		sparqlQuery = Pattern.compile("MINUS \\{(?s).*?}\\n {2}}").matcher(sparqlQuery).replaceAll("");
		try (SailRepositoryConnection connection = repository.getConnection()) {
			connection.setParserConfig(new ParserConfig().set(BasicParserSettings.VERIFY_URI_SYNTAX, false));

			Update preparedUpdate = connection.prepareUpdate(QueryLanguage.SPARQL, sparqlQuery);
			preparedUpdate.setMaxExecutionTime(timeout);

			Stopwatch stopwatch = Stopwatch.createStarted();
			preparedUpdate.execute();
			stopwatch.stop(); // optional
			logger.info("Time elapsed to execute update query: " + stopwatch.elapsed(TimeUnit.MILLISECONDS));
			if (out != null) {
				try (JsonGenerator gen = Json.createGenerator(out)) {
					gen.writeStartObject().write("ok", true).writeEnd();
				}
			}
		}
	}
}
