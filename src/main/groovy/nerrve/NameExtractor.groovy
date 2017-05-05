package nerrve

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.text.PDFTextStripper

public class NameExtractor {
    public static void main(String[] args) {
        def cliBuilder = new CliBuilder(usage: 'extract.bat "c:\\path\\to\\directory\\of\\invoice\\pdf\\files"')
        cliBuilder.with {
            h longOpt: 'help', "Print usage"
            d longOpt: 'directory', args: 1, argName: 'input-directory', 'Input Directory containing the Flipkart Labels PDF Files. The file name of the PDF Files should start with Flipkart-Labels'
        }
        def options = cliBuilder.parse(args)
        if (!options) {
            println "Please specify the 'directory' argument. Or type 'extract.bat -h' for help"
            cliBuilder.usage()
            return
        }
        if (options.h || !options.'directory') {
            cliBuilder.usage()
            return
        }

        File directory = new File((String) options.d)
        File nameAndInvoiceFile = new File(directory, "name-invoice.csv")
        PrintWriter writer = new PrintWriter(nameAndInvoiceFile)
        directory.eachFileRecurse { File f ->
            if (!f.name.endsWith("pdf") || !f.name.startsWith("Flipkart-Labels"))
                return
            List<Tuple> nameInvoiceTuples = new PDFInvoiceNameExtractor(f).nameAndInvoices
            for (Tuple t : nameInvoiceTuples) {
                writer.println("${t.get(0)},${t.get(1)}")
            }
        }
        writer.close()
    }

    private static String getInvoiceNumber(String text) {
        def extractedMatch = text =~ /Invoice No: ([^\s]+)/
        extractedMatch[0][1]
    }

    private static String getName(String text) {
        def extractedNameMatch = text =~ /(?ms)DELIVERY ADDRESS:(.*?),/
        String extractedName = extractedNameMatch[0][1]
        extractedName.replaceAll("[\r\n]", " ").replaceAll("  ", " ").trim()
    }

    static class PDFInvoiceNameExtractor {
        File file

        PDFInvoiceNameExtractor(File file) {
            this.file = file
        }

        List<Tuple> getNameAndInvoices() {
            println "Processing ${file.name}"
            List<Tuple> tuples = []
            PDDocument document = null
            try {
                document = PDDocument.load(file)
                PDFTextStripper textStripper = new PDFTextStripper()
                textStripper.sortByPosition = true
                for (int i = 1; i <= document.getNumberOfPages(); i++) {
                    tuples << getInvoiceNameTuple(i, textStripper, document)
                }
            } finally {
                document.close()
            }
            tuples
        }

        private static Tuple getInvoiceNameTuple(int pageNumber, PDFTextStripper textStripper, PDDocument document) {
            try {
                textStripper.startPage = pageNumber
                textStripper.endPage = pageNumber
                String text = textStripper.getText(document)
                String name = getName(text)
                String invoiceNumber = getInvoiceNumber(text)
                new Tuple(invoiceNumber, name)
            } catch (Exception e) {
                println("Failed while trying to extract for page number $pageNumber")
                e.printStackTrace()
            }
            throw new IllegalArgumentException()
        }
    }
}
